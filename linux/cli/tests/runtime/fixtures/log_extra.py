#!/usr/bin/env python3
"""User-profile log visibility, follow, level and redaction obligations."""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
import uuid

import probe
import setup

CLI = probe.CLI
CORE = probe.CORE
STATE = setup.STATE


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('--instance', required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    setup.guard(args.instance)
    out = args.out.resolve()
    if not out.is_relative_to(STATE / 'scenarios'):
        raise RuntimeError('Private fixture output required')
    out.mkdir(parents=True, exist_ok=False, mode=0o700)
    if Path('/run/xrayfa/session.json').exists():
        raise RuntimeError('Runtime journal must be resolved first')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node_record = next(n for n in nodes if '10.204.0.200:24402?' in n['url'])
    node = str(node_record['id'])
    report = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def user_call(*argv, timeout=35):
        return probe.cmd(list(argv), timeout=timeout, user=True)

    def case_folder(case_id):
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        # The CLI runs as xfa; every parent of its output path must therefore
        # be traversable by xfa. Evidence remains under the root-only scenario
        # tree and is copied back after the user operation completes.
        user_dir = Path('/home/xfa') / ('.xrayfa-log-' + uuid.uuid4().hex)
        user_dir.mkdir(mode=0o700)
        os.chown(user_dir, 1000, 1000)
        return folder, user_dir

    def check(user_dir, level='info', core=CORE):
        output = user_dir / f'check-{level}.json'
        result = user_call(CLI, 'check', node, '--output', str(output),
                           '--core', core, '--log-level', level)
        validation = output.with_name(output.stem + '.validation.json')
        if result['exit'] != 0 or not validation.exists():
            raise RuntimeError(f'check failed: {result}')
        return result, json.loads(validation.read_text()), Path(json.loads(validation.read_text())['logPath'])

    def add(case_id, action):
        folder, user_dir = case_folder(case_id)
        record = {'id': case_id, 'status': 'failed', 'evidenceDirectory': folder.name}
        try:
            record.update(action(folder, user_dir))
            record['status'] = 'passed'
        except Exception as exc:
            record['error'] = str(exc)
        finally:
            # Keep user-created files in the immutable evidence tree but make
            # the evidence readable by the root fixture collector.
            for source in user_dir.iterdir():
                target = folder / source.name
                if source.is_file() and not target.exists():
                    shutil.copy2(source, target)
            shutil.rmtree(user_dir, ignore_errors=True)
            report['cases'].append(record)
            save()
            print(json.dumps({'id': case_id, 'status': record['status'],
                              'error': record.get('error')}), flush=True)

    def realtime(folder, user_dir):
        command, validation, log_path = check(user_dir, 'info')
        text = log_path.read_text(errors='replace')
        if validation['sessionId'] not in text or 'START' not in text or 'END client=' not in text:
            raise RuntimeError('Realtime log did not contain complete session boundaries')
        return {'expectedExit': 0, 'command': command, 'sessionId': validation['sessionId'],
                'logPath': str(log_path), 'logBytes': len(text)}

    def persist(folder, user_dir):
        command, validation, log_path = check(user_dir, 'info')
        viewed = user_call(CLI, 'logs', '--tail', '100')
        if viewed['exit'] != 0 or validation['sessionId'] not in viewed['stdout']:
            raise RuntimeError('Persisted log was not visible to logs --tail')
        return {'expectedExit': 0, 'command': viewed, 'check': command,
                'sessionId': validation['sessionId']}

    def tail(folder, user_dir):
        _, validation, _ = check(user_dir, 'info')
        viewed = user_call(CLI, 'logs', '--tail', '2')
        lines = [line for line in viewed['stdout'].splitlines() if line.strip()]
        if viewed['exit'] != 0 or len(lines) > 2:
            raise RuntimeError('Tail returned more than the requested number of lines')
        return {'expectedExit': 0, 'command': viewed, 'sessionId': validation['sessionId'],
                'lineCount': len(lines)}

    def follow(folder, user_dir):
        env = {k: v for k, v in os.environ.items()
               if k.lower() not in ['http_proxy', 'https_proxy', 'all_proxy', 'no_proxy']}
        env.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', LANG='C', LC_ALL='C', SUDO_UID='1000')
        process = subprocess.Popen([CLI, 'logs', '--tail', '0', '--follow'],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   env=env, user=1000, group=1000, extra_groups=[],
                                   text=True)
        try:
            time.sleep(.4)
            check_result, validation, _ = check(user_dir, 'debug')
            time.sleep(.5)
            process.send_signal(signal.SIGINT)
            stdout, stderr = process.communicate(timeout=8)
        finally:
            if process.poll() is None:
                process.kill(); process.wait(timeout=3)
        if check_result['exit'] != 0 or process.returncode != 0 or validation['sessionId'] not in stdout:
            raise RuntimeError('Follow did not drain a new session before clean exit')
        return {'expectedExit': 0, 'command': {'argv': [CLI, 'logs', '--follow'],
                                               'exit': process.returncode,
                                               'stdout': stdout[-65536:], 'stderr': stderr[-65536:]},
                'check': check_result, 'sessionId': validation['sessionId']}

    def levels(folder, user_dir):
        _, debug_report, debug_path = check(user_dir, 'debug')
        _, error_report, error_path = check(user_dir, 'error')
        debug_text = debug_path.read_text(errors='replace')
        error_text = error_path.read_text(errors='replace')
        debug_lines = [line for line in debug_text.splitlines() if f'session={debug_report["sessionId"]} ' in line]
        error_lines = [line for line in error_text.splitlines() if f'session={error_report["sessionId"]} ' in line]
        if not debug_lines or not error_lines:
            raise RuntimeError('Level checks did not create session logs')
        if not any('DEBUG' in line for line in debug_lines):
            raise RuntimeError('Debug level did not retain debug records')
        if any('DEBUG' in line for line in error_lines):
            raise RuntimeError('Error level retained debug records')
        return {'expectedExit': 0, 'command': {'argv': [CLI, 'check'], 'exit': 0},
                'debugSession': debug_report['sessionId'], 'errorSession': error_report['sessionId']}

    def redaction(folder, user_dir):
        fake = Path('/opt/xrayfa-runtime/fixtures/log-redaction-core')
        secret = '11111111-2222-4333-8444-555555555555'
        fake.write_text('#!/usr/bin/python3\nimport sys\n'
                        f'print({secret!r})\n'
                        'raise SystemExit(0)\n')
        fake.chmod(0o755)
        os.chown(fake, 0, 0)
        try:
            command, validation, log_path = check(user_dir, 'debug', str(fake))
        finally:
            fake.unlink(missing_ok=True)
        text = log_path.read_text(errors='replace')
        secrets = [node_record['url'], secret]
        if any(secret in text for secret in secrets):
            raise RuntimeError('Raw node secret appeared in persisted log')
        if '[REDACTED]' not in text:
            raise RuntimeError('Redaction marker was not present in persisted log')
        return {'expectedExit': 0, 'command': command, 'sessionId': validation['sessionId']}

    add('LOG-01/realtime', realtime)
    add('LOG-01/persist', persist)
    add('LOG-01/tail', tail)
    add('LOG-01/follow', follow)
    add('LOG-01/levels', levels)
    add('LOG-01/redaction', redaction)
    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    print(json.dumps({'status': report['status'], 'cases': len(report['cases'])}))
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
