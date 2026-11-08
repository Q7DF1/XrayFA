#!/usr/bin/env python3
"""Additional preflight, identity and immutable-input obligations in the VM."""
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
    p = argparse.ArgumentParser()
    p.add_argument('--instance', required=True)
    p.add_argument('--out', type=Path, required=True)
    args = p.parse_args()
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
    result = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }

    def save():
        (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')

    def call(*argv, user=False, env=None, timeout=24):
        if env is None:
            return probe.cmd(list(argv), user=user, timeout=timeout)
        clean = {k: v for k, v in os.environ.items()
                 if k.lower() not in ['http_proxy', 'https_proxy', 'all_proxy', 'no_proxy']
                 and not k.startswith('XRAY')}
        for key, value in env.items():
            if value is None:
                clean.pop(key, None)
            else:
                clean[key] = value
        clean.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', LANG='C', LC_ALL='C')
        try:
            p = subprocess.run(list(argv), capture_output=True, text=True, timeout=timeout,
                               env=clean, **({'user': 1000, 'group': 1000, 'extra_groups': []}
                                              if user else {}))
            return {'argv': list(argv), 'exit': p.returncode, 'stdout': p.stdout[-65536:],
                    'stderr': p.stderr[-65536:]}
        except subprocess.TimeoutExpired:
            return {'argv': list(argv), 'exit': None, 'timeout': True, 'stdout': '', 'stderr': ''}

    def run_case(case_id, expected, action, assertion=lambda _: True):
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        before = probe.snapshot()
        record = {'id': case_id, 'expectedExit': expected, 'status': 'failed',
                  'before': before, 'evidenceDirectory': folder.name}
        try:
            record['command'] = action(folder)
            after = probe.snapshot()
            record['after'] = after
            record['networkUnchanged'] = probe.config_snapshot(before) == probe.config_snapshot(after)
            record['noJournal'] = not journal.exists()
            record['assertion'] = assertion(record)
            record['status'] = 'passed' if (record['command']['exit'] == expected and
                                            record['networkUnchanged'] and record['noJournal'] and
                                            record['assertion']) else 'failed'
        except Exception as exc:
            record['error'] = str(exc)
        result['cases'].append(record)
        save()
        print(json.dumps({'id': case_id, 'status': record['status'],
                          'error': record.get('error')}), flush=True)
        if journal.exists():
            raise RuntimeError('Unexpected retained journal after isolated preflight case')

    def doctor_variant(folder, env):
        r = call(CLI, 'doctor', '--core', CORE, '--json', env=env)
        (folder / 'doctor.json').write_text(r['stdout'])
        return r

    run_case('AUTH-02/sudo', 0,
             lambda folder: doctor_variant(folder, {'SUDO_UID': '1000', 'HOME': '/root'}),
             lambda r: json.loads(r['command']['stdout'])['uid'] == 1000 and
             json.loads(r['command']['stdout'])['profile'] == '/home/xfa/.local/share/xrayfa')
    run_case('AUTH-02/root', 0,
             lambda folder: doctor_variant(folder, {'SUDO_UID': None, 'HOME': '/root'}),
             lambda r: json.loads(r['command']['stdout'])['uid'] == 0 and
             json.loads(r['command']['stdout'])['effectiveUid'] == 0)
    run_case('AUTH-02/forged-home', 0,
             lambda folder: doctor_variant(folder, {'SUDO_UID': '1000', 'HOME': '/root',
                                                    'XRAYFA_HOME': '/root/forged'}),
             lambda r: json.loads(r['command']['stdout'])['profile'] == '/home/xfa/.local/share/xrayfa')
    run_case('AUTH-02/forged-xrayfa-home', 0,
             lambda folder: doctor_variant(folder, {'SUDO_UID': '1000', 'HOME': '/tmp',
                                                    'XRAYFA_HOME': '/tmp/forged'}),
             lambda r: json.loads(r['command']['stdout'])['profile'] == '/home/xfa/.local/share/xrayfa')

    def fake_core(folder, mode):
        path = folder / 'fake-core'
        literal = str(path)
        body = [
            '#!/usr/bin/python3', 'import pathlib,sys',
            'if sys.argv[1:] == ["version"]:',
            '    print("Xray fixture 1.0")',
        ]
        if mode == 'core-replaced':
            body += [f'    pathlib.Path({literal!r}).write_text("#!/bin/sh\\nexit 0\\n")']
        body += ['    raise SystemExit(0)',
                 'if sys.argv[1:4] != ["run", "-test", "-c"]: raise SystemExit(2)']
        if mode == 'test-failure':
            body += ['print("synthetic core validation failure", file=sys.stderr)', 'raise SystemExit(23)']
        elif mode == 'config-replaced':
            body += ['pathlib.Path(sys.argv[4]).write_text("{}\\n")',
                     'print("Configuration OK.")', 'raise SystemExit(0)']
        else:
            body += ['print("Configuration OK.")', 'raise SystemExit(0)']
        path.write_text('\n'.join(body) + '\n')
        path.chmod(0o700)
        os.chown(path, 0, 0)
        return str(path)

    for mode, expected in [('test-failure', 6), ('config-replaced', 6), ('core-replaced', 5)]:
        run_case('PRE-01/' + mode, expected,
                 lambda folder, mode=mode: call(CLI, 'run', str(node), '--core', fake_core(folder, mode)))

    def conflict_case(folder, kind):
        if kind == 'interface':
            call('/usr/sbin/ip', 'link', 'add', 'xrayfa0', 'type', 'dummy')
            try:
                return call(CLI, 'run', str(node), '--core', CORE)
            finally:
                call('/usr/sbin/ip', 'link', 'delete', 'xrayfa0')
        if kind == 'address':
            call('/usr/sbin/ip', 'link', 'add', 'xfa-conflict', 'type', 'dummy')
            call('/usr/sbin/ip', 'link', 'set', 'xfa-conflict', 'up')
            call('/usr/sbin/ip', 'address', 'add', '198.18.0.1/30', 'dev', 'xfa-conflict')
            try:
                return call(CLI, 'run', str(node), '--core', CORE)
            finally:
                call('/usr/sbin/ip', 'link', 'delete', 'xfa-conflict')
        if kind == 'vpn':
            call('/usr/sbin/ip', 'tuntap', 'add', 'dev', 'xfa-vpn', 'mode', 'tun')
            try:
                return call(CLI, 'run', str(node), '--core', CORE)
            finally:
                call('/usr/sbin/ip', 'link', 'delete', 'xfa-vpn')
        if kind == 'dns':
            changed = call('/usr/bin/resolvectl', 'domain', 'ens2', 'conflict.xfa.test')
            if changed['exit'] != 0:
                raise RuntimeError('DNS conflict fixture could not be applied')
            try:
                return call(CLI, 'run', str(node), '--core', CORE)
            finally:
                call('/usr/bin/resolvectl', 'domain', 'ens2', '')
        if kind == 'route':
            changed = call('/usr/sbin/ip', 'rule', 'add', 'priority', '100',
                           'from', '10.0.2.0/24', 'table', '100')
            if changed['exit'] != 0:
                raise RuntimeError('Route conflict fixture could not be applied')
            try:
                return call(CLI, 'run', str(node), '--core', CORE)
            finally:
                call('/usr/sbin/ip', 'rule', 'del', 'priority', '100',
                     'from', '10.0.2.0/24', 'table', '100')
        raise RuntimeError('Unknown conflict')

    for kind, expected in [('interface', 8), ('address', 8), ('vpn', 5), ('dns', 5), ('route', 5)]:
        run_case('PRE-03/' + kind, expected,
                 lambda folder, kind=kind: conflict_case(folder, kind))

    # A real active session is used for this case; the second run must fail
    # before changing the first session's journal or network state.
    folder = out / 'PRE-03-active-session'
    folder.mkdir(mode=0o700)
    before = probe.snapshot()
    controller = subprocess.Popen([CLI, 'run', str(node), '--core', CORE],
                                  stdout=(folder / 'controller.log').open('wb'),
                                  stderr=subprocess.STDOUT,
                                  env={**os.environ, 'PATH': '/usr/sbin:/usr/bin:/sbin:/bin',
                                       'LANG': 'C', 'LC_ALL': 'C', 'SUDO_UID': '1000'})
    try:
        deadline = time.monotonic() + 17
        while time.monotonic() < deadline and not journal.exists() and controller.poll() is None:
            time.sleep(.05)
        record = {'id': 'PRE-03/active-session', 'expectedExit': 8, 'status': 'failed',
                  'before': before, 'command': call(CLI, 'run', str(node), '--core', CORE),
                  'journalPreserved': journal.exists()}
        controller.send_signal(signal.SIGTERM)
        controller.wait(timeout=22)
        record['controllerExit'] = controller.returncode
        record['after'] = probe.snapshot()
        record['status'] = 'passed' if record['command']['exit'] == 8 and record['journalPreserved'] and \
            probe.config_snapshot(before) == probe.config_snapshot(record['after']) else 'failed'
    finally:
        if controller.poll() is None:
            controller.kill(); controller.wait(timeout=3)
        if journal.exists():
            call(CLI, 'recover', timeout=24)
    result['cases'].append(record)
    save()
    print(json.dumps({'id': record['id'], 'status': record['status']}), flush=True)

    result['status'] = 'passed' if all(c['status'] == 'passed' for c in result['cases']) else 'failed'
    save()
    print(json.dumps({'status': result['status'], 'cases': len(result['cases'])}))
    raise SystemExit(result['status'] != 'passed')


if __name__ == '__main__':
    main()
