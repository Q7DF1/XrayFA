#!/usr/bin/env python3
"""Controlled log-file failure while a real runtime session is supervised."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid

import probe
import setup

CLI = probe.CLI
CORE = probe.CORE
STATE = setup.STATE
LOG = Path('/var/log/xrayfa/1000/xrayfa.log')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('--instance', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--only-write', action='store_true')
    parser.add_argument('--only-full', action='store_true')
    args = parser.parse_args()
    setup.guard(args.instance)
    out = args.out.resolve()
    if not out.is_relative_to(STATE / 'scenarios'):
        raise RuntimeError('Private fixture output required')
    out.mkdir(parents=True, exist_ok=False, mode=0o700)
    journal = Path('/run/xrayfa/session.json')
    if journal.exists():
        raise RuntimeError('Existing journal must be resolved first')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node = next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    report = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ['http_proxy', 'https_proxy', 'all_proxy', 'no_proxy']}
    env.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', LANG='C', LC_ALL='C', SUDO_UID='1000')

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def run_case(case_id, kill_core, full=False):
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        before = probe.snapshot()
        log_file = (folder / 'cli.log').open('wb')
        process = subprocess.Popen([CLI, 'run', str(node), '--core', CORE, '--log-level', 'debug'],
                                   stdout=log_file, stderr=subprocess.STDOUT, env=env)
        record = {'id': case_id, 'status': 'failed', 'before': before}
        changed = False
        try:
            deadline = time.monotonic() + 18
            while time.monotonic() < deadline and process.poll() is None:
                if journal.exists() and json.loads(journal.read_text()).get('phase') == 'Running':
                    break
                time.sleep(.05)
            if not journal.exists():
                raise RuntimeError('Runtime did not reach Running')
            current = json.loads(journal.read_text())
            record['journal'] = current
            if not LOG.exists():
                raise RuntimeError('System log file is absent')
            if full:
                LOG.unlink()
                LOG.symlink_to('/dev/full')
            else:
                LOG.chmod(0o644)
            changed = True
            if kill_core or full:
                request_nonce = uuid.uuid4().hex
                record['triggerRequests'] = [probe.cmd(
                    ['curl', '--noproxy', '*', '--max-time', '3', '--fail', '--silent',
                     f'http://203.0.113.10/{request_nonce}/{i}'], user=True)
                    for i in range(4)]
            if kill_core:
                failure_deadline = time.monotonic() + 6
                while time.monotonic() < failure_deadline and process.poll() is None:
                    if 'Log persistence failed' in (folder / 'cli.log').read_text(errors='replace'):
                        record['logFailureObservedBeforeCoreKill'] = True
                        break
                    time.sleep(.02)
                else:
                    record['logFailureObservedBeforeCoreKill'] = False
                os.kill(current['corePid'], signal.SIGKILL)
            process.wait(timeout=24)
            record['command'] = {'argv': [CLI, 'run', str(node), '--core', CORE],
                                 'exit': process.returncode}
            record['logModeAfterFault'] = oct(LOG.stat().st_mode & 0o777) if LOG.exists() else None
            if process.returncode != 11:
                raise RuntimeError(f'Expected controlled logging failure 11, got {process.returncode}')
            record['observedFailure'] = True
        except Exception as exc:
            record['error'] = str(exc)
        finally:
            if process.poll() is None:
                process.send_signal(signal.SIGTERM)
                try:
                    process.wait(timeout=24)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=3)
            if changed:
                if LOG.is_symlink():
                    LOG.unlink()
                if not LOG.exists():
                    LOG.touch()
                os.chown(LOG, 0, 0)
                LOG.chmod(0o600)
            if journal.exists():
                record['recover'] = probe.cmd([CLI, 'recover'], timeout=24)
            log_file.close()
        record['after'] = probe.snapshot()
        record['baselineEqual'] = probe.config_snapshot(before) == probe.config_snapshot(record['after'])
        record['noJournal'] = not journal.exists()
        if record.get('command', {}).get('exit') == 11 and record['baselineEqual'] and record['noJournal']:
            record['status'] = 'passed'
        report['cases'].append(record)
        save()
        print(json.dumps({'id': case_id, 'status': record['status'],
                          'error': record.get('error')}), flush=True)

    if args.only_full:
        run_case('LOG-02/full', False, full=True)
    else:
        run_case('LOG-02/write-failure', False)
    if not args.only_write and not args.only_full:
        run_case('LOG-02/simultaneous-core-failure', True)
    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    print(json.dumps({'status': report['status'], 'cases': len(report['cases'])}))
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
