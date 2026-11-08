#!/usr/bin/env python3
"""Exercise the durable lease write/fsync and partial-intent recovery contracts."""
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
LOADER = '/lib64/ld-linux-x86-64.so.2'
STRACE = '/opt/xrayfa-runtime/test-tools/strace'


def wait_running(journal, process, timeout=18):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline and process.poll() is None:
        if journal.exists():
            try:
                if json.loads(journal.read_text()).get('phase') == 'Running':
                    return json.loads(journal.read_text())
            except (OSError, json.JSONDecodeError):
                pass
        time.sleep(.05)
    raise RuntimeError('Runtime did not reach Running')


def stop_process(process):
    if process.poll() is None:
        process.send_signal(signal.SIGTERM)
        try:
            process.wait(timeout=24)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('--instance', required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    setup.guard(args.instance)
    out = args.out.resolve()
    if not out.is_relative_to(STATE / 'scenarios'):
        raise RuntimeError('Private scenario output required')
    out.mkdir(parents=True, exist_ok=False, mode=0o700)
    journal = Path('/run/xrayfa/session.json')
    if journal.exists():
        raise RuntimeError('Existing product journal must be resolved first')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node = next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    report = {
        'schemaVersion': 1,
        'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI),
        'coreSha256': probe.sha(CORE),
        'fixtureSha256': probe.sha(__file__),
        'cases': [],
    }

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def injection_case(case_id, trace_name, syscall, ordinal):
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        before = probe.snapshot()
        trace = folder / trace_name
        command = probe.cmd([
            LOADER, '--library-path', '/opt/xrayfa-runtime/test-tools/lib', STRACE,
            '-o', str(trace), '-e', f'trace={syscall}',
            '-e', f'inject={syscall}:error=EIO:when={ordinal}',
            CLI, 'run', str(node), '--core', CORE,
        ], timeout=35)
        after = probe.snapshot()
        record = {
            'id': case_id,
            'status': 'failed',
            'before': before,
            'command': command,
            'trace': trace.name,
            'injectedSyscall': syscall,
            'injectedOrdinal': ordinal,
            'journalAfterCommand': journal.exists(),
            'after': after,
        }
        record['baselineEqual'] = probe.config_snapshot(before) == probe.config_snapshot(after)
        record['noJournal'] = not journal.exists()
        record['coreStarted'] = 'stage=Running' in command.get('stdout', '') or 'stage=Running' in command.get('stderr', '')
        record['status'] = 'passed' if (
            command.get('exit') == 10 and record['baselineEqual'] and
            record['noJournal'] and not record['coreStarted']
        ) else 'failed'
        report['cases'].append(record)
        save()
        print(json.dumps({'id': case_id, 'status': record['status'], 'exit': command.get('exit')}), flush=True)

    injection_case('LEASE-01/write', 'write.strace', 'write', 14)
    if journal.exists():
        raise RuntimeError('write injection retained a journal')
    injection_case('LEASE-01/fsync', 'fsync.strace', 'fsync', 3)
    if journal.exists():
        raise RuntimeError('fsync injection retained a journal')

    folder = out / 'LEASE-01-partial-intent'
    folder.mkdir(mode=0o700)
    before = probe.snapshot()
    log = (folder / 'cli.log').open('wb')
    process = subprocess.Popen([CLI, 'run', str(node), '--core', CORE, '--log-level', 'debug'],
                               stdout=log, stderr=subprocess.STDOUT)
    record = {'id': 'LEASE-01/partial-intent', 'status': 'failed', 'before': before}
    table = None
    try:
        current = wait_running(journal, process)
        table = current['nftTable']
        record['journal'] = current
        record['nftTable'] = table
        # Freeze the controller while a fixture-only rule changes the committed
        # after-image. The controller is then killed, leaving a real owned journal
        # for recover to reconcile.
        os.kill(process.pid, signal.SIGSTOP)
        record['fixtureMutation'] = probe.cmd([
            'nft', 'add', 'rule', 'inet', table, 'output', 'counter', 'accept',
            'comment', 'lease-extra-mismatch',
        ])
        os.kill(process.pid, signal.SIGKILL)
        process.wait(timeout=5)
        record['controllerExit'] = process.returncode
        record['firstRecover'] = probe.cmd([CLI, 'recover'], timeout=30)
        # The manifest's CLI contract describes the intentionally incomplete
        # first recovery; the retry is recorded separately as fault-removal
        # evidence.
        record['command'] = record['firstRecover']
        record['journalRetainedAfterFirstRecover'] = journal.exists()
        record['snapshotAfterFirstRecover'] = probe.snapshot()
        if record['firstRecover'].get('exit') != 9 or not record['journalRetainedAfterFirstRecover']:
            raise RuntimeError('Changed nft after-image was not preserved as incomplete recovery')

        record['faultRemoval'] = probe.cmd(['nft', 'delete', 'table', 'inet', table], timeout=8)
        record['secondRecover'] = probe.cmd([CLI, 'recover'], timeout=30)
        record['journalRetainedAfterSecondRecover'] = journal.exists()
        record['after'] = probe.snapshot()
        record['baselineEqual'] = probe.config_snapshot(before) == probe.config_snapshot(record['after'])
        record['status'] = 'passed' if (
            record['secondRecover'].get('exit') == 0 and
            not record['journalRetainedAfterSecondRecover'] and
            record['baselineEqual']
        ) else 'failed'
    except Exception as exc:
        record['error'] = str(exc)
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=5)
        log.close()
        # Leave no fixture table behind. If the first recovery stopped before the
        # nft step, this is the only remaining synthetic resource.
        if table:
            probe.cmd(['nft', 'delete', 'table', 'inet', table], timeout=8)
        if journal.exists():
            record['finalRecover'] = probe.cmd([CLI, 'recover'], timeout=30)
        record.setdefault('after', probe.snapshot())
        record.setdefault('baselineEqual', probe.config_snapshot(before) == probe.config_snapshot(record['after']))
        record['journalRetainedFinally'] = journal.exists()
        report['cases'].append(record)
        save()
        print(json.dumps({'id': record['id'], 'status': record['status'], 'error': record.get('error')}), flush=True)

    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
