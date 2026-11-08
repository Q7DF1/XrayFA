#!/usr/bin/env python3
"""Fixture baseline reconciliation for individual cases and the suite."""
import argparse
import json
import os
from pathlib import Path

import probe
import setup

CLI = probe.CLI
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
        raise RuntimeError('Existing journal must be resolved first')
    report = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(probe.CLI), 'coreSha256': probe.sha(probe.CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def run_case(case_id, calls):
        before = probe.snapshot()
        commands = [probe.cmd([CLI, 'recover'], timeout=24) for _ in range(calls)]
        after = probe.snapshot()
        equal = probe.config_snapshot(before) == probe.config_snapshot(after)
        record = {
            'id': case_id, 'expectedExit': 0, 'commands': commands,
            'command': commands[-1], 'before': before, 'after': after,
            'networkUnchanged': equal, 'noJournal': not Path('/run/xrayfa/session.json').exists(),
            'status': 'passed' if all(c['exit'] == 0 for c in commands) and equal and
                not Path('/run/xrayfa/session.json').exists() else 'failed'
        }
        report['cases'].append(record)
        save()
        print(json.dumps({'id': case_id, 'status': record['status']}), flush=True)

    run_case('BASE-01/each-case', 1)
    run_case('BASE-01/suite', 3)
    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    print(json.dumps({'status': report['status'], 'cases': len(report['cases'])}))
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
