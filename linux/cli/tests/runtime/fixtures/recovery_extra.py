#!/usr/bin/env python3
"""Idempotent recovery and retry-after-foreign-change obligations."""
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
    journal = Path('/run/xrayfa/session.json')
    if journal.exists():
        raise RuntimeError('Existing journal preserved')
    report = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(probe.CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def recover():
        return probe.cmd([CLI, 'recover'], timeout=24)

    def add_case(case_id, action):
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        record = {'id': case_id, 'status': 'failed', 'evidenceDirectory': folder.name}
        try:
            record.update(action(folder))
            record['status'] = 'passed'
        except Exception as exc:
            record['error'] = str(exc)
        finally:
            if journal.exists():
                record['retainedJournal'] = journal.read_text()
                journal.unlink()
            report['cases'].append(record)
            save()
            print(json.dumps({'id': case_id, 'status': record['status'],
                              'error': record.get('error')}), flush=True)
        if journal.exists():
            raise RuntimeError('Recovery journal remained after isolated case')

    def no_journal_case(folder, repeat):
        before = probe.snapshot()
        calls = [recover() for _ in range(repeat)]
        after = probe.snapshot()
        if any(c['exit'] != 0 for c in calls):
            raise RuntimeError('Idempotent recover call failed')
        if probe.config_snapshot(before) != probe.config_snapshot(after):
            raise RuntimeError('No-journal recover changed network state')
        return {'expectedExit': 0, 'calls': calls, 'before': before, 'after': after,
                'networkUnchanged': True, 'noJournal': not journal.exists(),
                'command': calls[-1]}

    add_case('REC-02/first', lambda folder: no_journal_case(folder, 1))
    add_case('REC-02/second', lambda folder: no_journal_case(folder, 2))

    def retry_case(folder):
        sources = sorted(Path('/var/log/xrayfa/1000').glob('recovery-*.json'),
                         key=lambda p: p.stat().st_mtime, reverse=True)
        source = next(p for p in sources if json.loads(p.read_text()).get('tunAfter'))
        base = json.loads(source.read_text())
        base['phase'] = 'Running'
        before = probe.snapshot()
        created = probe.cmd(['ip', 'link', 'add', 'xrayfa0', 'type', 'dummy'])
        if created['exit'] != 0:
            raise RuntimeError('Foreign interface fixture failed')
        try:
            journal.write_text(json.dumps(base, indent=2) + '\n')
            journal.chmod(0o600)
            first = recover()
            if first['exit'] != 9 or not journal.exists():
                raise RuntimeError('First recovery did not retain the foreign-change journal')
            (folder / 'first-failure-journal.json').write_bytes(journal.read_bytes())
            foreign = json.loads(probe.cmd(['ip', '-j', 'link', 'show', 'dev', 'xrayfa0'])['stdout'])[0]
            delete = probe.cmd(['ip', 'link', 'delete', 'xrayfa0'])
            if delete['exit'] != 0:
                raise RuntimeError('Foreign interface removal failed')
            second = recover()
            after = probe.snapshot()
            if second['exit'] != 0 or journal.exists():
                raise RuntimeError('Recovery retry did not complete')
            if probe.config_snapshot(before) != probe.config_snapshot(after):
                raise RuntimeError('Retry recovery did not restore exact baseline')
            return {'expectedExit': 0, 'firstFailure': first, 'command': second,
                    'foreignInterface': foreign, 'afterFaultRemoval': delete,
                    'after': after, 'networkUnchanged': True, 'noJournal': True}
        finally:
            if journal.exists():
                journal.write_text(json.dumps(base, indent=2) + '\n')
                journal.chmod(0o600)
            left = probe.cmd(['ip', '-j', 'link', 'show', 'dev', 'xrayfa0'])
            if left['exit'] == 0:
                probe.cmd(['ip', 'link', 'delete', 'xrayfa0'])
            if journal.exists():
                retry = recover()
                (folder / 'final-recovery.json').write_text(json.dumps(retry, indent=2) + '\n')

    add_case('REC-02/retry-after-failure', retry_case)

    add_case('EXIT-01/repeat-cleanup', lambda folder: no_journal_case(folder, 3))
    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    print(json.dumps({'status': report['status'], 'cases': len(report['cases'])}))
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
