#!/usr/bin/env python3
"""One current-hash long-idle runtime session for LOG-01."""
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
    journal = Path('/run/xrayfa/session.json')
    if journal.exists():
        raise RuntimeError('Existing journal must be resolved first')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node = next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    before = probe.snapshot()
    record = {
        'id': 'LOG-01/idle-70s', 'expectedExit': 0, 'status': 'failed',
        'before': before, 'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip()
    }
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ['http_proxy', 'https_proxy', 'all_proxy', 'no_proxy']}
    env.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', LANG='C', LC_ALL='C', SUDO_UID='1000')
    log_file = (out / 'cli.log').open('wb')
    process = subprocess.Popen([CLI, 'run', str(node), '--core', CORE, '--log-level', 'debug'],
                               stdout=log_file, stderr=subprocess.STDOUT, env=env)
    nonce = uuid.uuid4().hex
    try:
        deadline = time.monotonic() + 18
        while time.monotonic() < deadline and process.poll() is None and not journal.exists():
            time.sleep(.05)
        if not journal.exists():
            raise RuntimeError('Long-idle session did not reach Running')
        record['journalBeforeIdle'] = json.loads(journal.read_text())
        record['idleSeconds'] = 70
        started = time.monotonic()
        time.sleep(70)
        record['idleElapsed'] = time.monotonic() - started
        request = probe.cmd(['curl', '--noproxy', '*', '--max-time', '4', '--fail',
                             '--silent', '--show-error', f'http://203.0.113.10/{nonce}'], user=True)
        record['afterIdleRequest'] = request
        process.send_signal(signal.SIGTERM)
        process.wait(timeout=24)
        record['command'] = {'argv': [CLI, 'run', str(node), '--core', CORE],
                             'exit': process.returncode}
        record['recover'] = probe.cmd([CLI, 'recover'], timeout=24)
        record['recoverAgain'] = probe.cmd([CLI, 'recover'], timeout=24)
        record['after'] = probe.snapshot()
        record['networkUnchanged'] = probe.config_snapshot(before) == probe.config_snapshot(record['after'])
        record['noJournal'] = not journal.exists()
        record['status'] = 'passed' if (
            record['idleElapsed'] >= 70 and request['exit'] == 0 and nonce in request['stdout'] and
            record['command']['exit'] == 0 and record['recover']['exit'] == 0 and
            record['recoverAgain']['exit'] == 0 and record['networkUnchanged'] and record['noJournal']
        ) else 'failed'
    except Exception as exc:
        record['error'] = str(exc)
    finally:
        if process.poll() is None:
            process.send_signal(signal.SIGTERM)
            try:
                process.wait(timeout=24)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=3)
        if journal.exists():
            record['finalRecover'] = probe.cmd([CLI, 'recover'], timeout=24)
        log_file.close()
        (out / 'result.json').write_text(json.dumps({
            'schemaVersion': 1, 'instance': args.instance,
            'boot': record['boot'], 'cliSha256': probe.sha(CLI),
            'coreSha256': probe.sha(CORE), 'fixtureSha256': probe.sha(__file__),
            'cases': [record], 'status': record['status']
        }, indent=2) + '\n')
    print(json.dumps({'id': record['id'], 'status': record['status'],
                      'error': record.get('error')}), flush=True)
    raise SystemExit(record['status'] != 'passed')


if __name__ == '__main__':
    main()
