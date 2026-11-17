#!/usr/bin/env python3
"""Synthetic-core process lifecycle obligations in the private VM."""
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
REAL_CORE = probe.CORE
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
        raise RuntimeError('Private fixture scenario path required')
    out.mkdir(parents=True, exist_ok=False, mode=0o700)
    journal = Path('/run/xrayfa/session.json')
    if journal.exists():
        raise RuntimeError('Existing runtime journal must be resolved first')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node = next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    report = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(REAL_CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def call(*argv, timeout=24):
        return probe.cmd(list(argv), timeout=timeout)

    def fake_core(folder, mode):
        path = folder / 'fake-core'
        real = str(REAL_CORE)
        lines = [
            '#!/usr/bin/python3',
            'import os, signal, subprocess, sys, time',
            f'REAL = {real!r}',
            'args = sys.argv[1:]',
            'if args == ["version"]:',
            '    print("Xray fixture wrapper 1.0")',
            '    raise SystemExit(0)',
            'if args[:3] == ["run", "-test", "-c"]:',
            '    p = subprocess.run([REAL] + args, env={**os.environ, "XRAY_LOCATION_ASSET": "/opt/xrayfa-runtime"})',
            '    raise SystemExit(p.returncode)',
            'if args[:2] != ["run", "-c"]:',
            '    raise SystemExit(2)',
        ]
        if mode == 'early-exit':
            lines += ['raise SystemExit(0)']
        elif mode == 'nonzero-exit':
            lines += ['raise SystemExit(23)']
        elif mode == 'zero-exit':
            lines += ['raise SystemExit(0)']
        elif mode == 'readiness-timeout':
            lines += ['signal.signal(signal.SIGTERM, signal.SIG_DFL)',
                      'time.sleep(60)']
        elif mode == 'ignore-term':
            lines += ['signal.signal(signal.SIGTERM, signal.SIG_IGN)',
                      'p = subprocess.Popen([REAL] + args, env={**os.environ, "XRAY_LOCATION_ASSET": "/opt/xrayfa-runtime"})',
                      'raise SystemExit(p.wait())']
        elif mode == 'held-output':
            lines += ['sys.stdout.write("O" * 1048576); sys.stdout.flush()',
                      'sys.stderr.write("E" * 1048576); sys.stderr.flush()',
                      'time.sleep(60)']
        else:
            raise RuntimeError('Unknown synthetic core mode')
        path.write_text('\n'.join(lines) + '\n')
        path.chmod(0o700)
        os.chown(path, 0, 0)
        return str(path)

    modes = [
        ('PROC-01/readiness-timeout', 'readiness-timeout', 7),
        ('PROC-01/early-exit', 'early-exit', 7),
        ('PROC-01/nonzero-exit', 'nonzero-exit', 7),
        ('PROC-01/zero-exit', 'zero-exit', 7),
        ('PROC-02/ignore-term', 'ignore-term', 0),
        ('PROC-02/held-output', 'held-output', 7),
    ]
    for case_id, mode, expected in modes:
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        before = probe.snapshot()
        record = {'id': case_id, 'expectedExit': expected, 'status': 'failed',
                  'before': before, 'evidenceDirectory': folder.name}
        try:
            core = fake_core(folder, mode)
            with (folder / 'cli.log').open('wb') as log:
                proc = subprocess.Popen([CLI, 'run', str(node), '--core', core,
                                         '--log-level', 'debug'], stdout=log,
                                        stderr=subprocess.STDOUT,
                                        env={**os.environ, 'PATH': '/usr/sbin:/usr/bin:/sbin:/bin',
                                             'LANG': 'C', 'LC_ALL': 'C', 'SUDO_UID': '1000'})
                if mode == 'ignore-term':
                    deadline = time.monotonic() + 17
                    while time.monotonic() < deadline and not journal.exists() and proc.poll() is None:
                        time.sleep(.05)
                    record['runningJournal'] = journal.exists()
                    if not record['runningJournal']:
                        raise RuntimeError('ignore-term fixture did not reach Running')
                    proc.send_signal(signal.SIGTERM)
                try:
                    proc.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=3)
                    raise RuntimeError('Synthetic core process exceeded cleanup deadline')
                record['command'] = {'argv': [CLI, 'run', str(node), '--core', core],
                                     'exit': proc.returncode}
            record['log'] = (folder / 'cli.log').read_text(errors='replace')[-131072:]
            after = probe.snapshot()
            record['after'] = after
            record['networkUnchanged'] = probe.config_snapshot(before) == probe.config_snapshot(after)
            record['noJournal'] = not journal.exists()
            if mode == 'ignore-term':
                record['forcedGroupCleanupObserved'] = 'END runtime exit=0' in record['log']
            if mode == 'held-output':
                record['boundedOutputObserved'] = 'dropped_bytes=' in record['log'] or len(record['log']) < 300000
            record['status'] = 'passed' if (
                record['command']['exit'] == expected and record['networkUnchanged'] and
                record['noJournal'] and record.get('forcedGroupCleanupObserved', True) and
                record.get('boundedOutputObserved', True)
            ) else 'failed'
        except Exception as exc:
            record['error'] = str(exc)
        finally:
            if journal.exists():
                record['finalRecover'] = call(CLI, 'recover', timeout=24)
            report['cases'].append(record)
            save()
            print(json.dumps({'id': case_id, 'status': record['status'],
                              'error': record.get('error')}), flush=True)
            if journal.exists():
                raise RuntimeError('Retained journal blocks further isolated cases')

    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    print(json.dumps({'status': report['status'], 'cases': len(report['cases'])}))
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
