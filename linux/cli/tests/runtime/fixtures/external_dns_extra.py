#!/usr/bin/env python3
"""External DNS mutation during a live session and retained-journal recovery."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time

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
    record = {'id': 'EXT-01/dns-changed', 'expectedExit': 9, 'status': 'failed',
              'before': before}
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ['http_proxy', 'https_proxy', 'all_proxy', 'no_proxy']}
    env.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', LANG='C', LC_ALL='C', SUDO_UID='1000')
    process = subprocess.Popen([CLI, 'run', str(node), '--core', CORE, '--log-level', 'debug'],
                               stdout=(out / 'cli.log').open('wb'), stderr=subprocess.STDOUT,
                               env=env)
    changed = False
    try:
        deadline = time.monotonic() + 18
        while time.monotonic() < deadline and process.poll() is None:
            if journal.exists() and json.loads(journal.read_text()).get('phase') == 'Running':
                break
            time.sleep(.05)
        if not journal.exists():
            raise RuntimeError('Live session did not reach Running')
        record['journalBeforeFault'] = json.loads(journal.read_text())
        fault = probe.cmd(['/usr/bin/resolvectl', 'dns', 'ens2', '9.9.9.9'])
        record['fault'] = fault
        if fault['exit'] != 0:
            raise RuntimeError('DNS mutation fixture failed')
        changed = True
        process.wait(timeout=22)
        record['command'] = {'argv': [CLI, 'run', str(node), '--core', CORE],
                             'exit': process.returncode}
        record['journalRetained'] = journal.exists()
        if process.returncode != 9 or not record['journalRetained']:
            raise RuntimeError('External DNS mutation did not produce retained exit 9')
        record['removeFault'] = probe.cmd(['/usr/bin/resolvectl', 'dns', 'ens2', ''])
        changed = False
        record['recover'] = probe.cmd([CLI, 'recover'], timeout=24)
        record['recoverAgain'] = probe.cmd([CLI, 'recover'], timeout=24)
        record['after'] = probe.snapshot()
        record['baselineEqual'] = probe.config_snapshot(before) == probe.config_snapshot(record['after'])
        record['noJournal'] = not journal.exists()
        record['status'] = 'passed' if record['recover']['exit'] == 0 and \
            record['recoverAgain']['exit'] == 0 and record['baselineEqual'] and record['noJournal'] else 'failed'
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
            record['finalRemoveFault'] = probe.cmd(['/usr/bin/resolvectl', 'dns', 'ens2', ''])
        if journal.exists():
            record['finalRecover'] = probe.cmd([CLI, 'recover'], timeout=24)
        result = {
            'schemaVersion': 1, 'instance': args.instance,
            'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
            'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(CORE),
            'fixtureSha256': probe.sha(__file__), 'cases': [record],
            'status': record['status']
        }
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'id': record['id'], 'status': record['status'],
                      'error': record.get('error')}), flush=True)
    raise SystemExit(record['status'] != 'passed')


if __name__ == '__main__':
    main()
