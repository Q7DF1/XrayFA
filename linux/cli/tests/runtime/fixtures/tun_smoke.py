#!/usr/bin/env python3
"""Explicit --tun lifecycle regression, run only inside the dedicated runtime VM.

The operator link and all logs stay in private guest files. Reports contain only
request status codes and restoration results, never the link or response bodies.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import tempfile
import time

from tun_soak import monitor_tun_session


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--instance', required=True)
    parser.add_argument('--cli', type=Path, required=True)
    parser.add_argument('--core', type=Path, required=True)
    parser.add_argument('--link-file', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--skip-traffic', action='store_true', help='Test lifecycle only; makes no remote connectivity claim')
    parser.add_argument('--soak-seconds', type=int, default=0)
    parser.add_argument('--soak-interval', type=int, default=30)
    parser.add_argument('--request-timeout', type=int, default=45)
    parser.add_argument('--web-runner', type=Path, default=Path(__file__).resolve().parents[2] / 'run_web_acceptance.py')
    args = parser.parse_args()
    if args.soak_seconds < 0 or args.soak_interval < 1 or args.request_timeout < 1:
        parser.error('Invalid soak duration, interval, or timeout')
    if args.soak_seconds and args.skip_traffic:
        parser.error('Soak requires traffic checks')
    if (os.geteuid() != 0 or
            Path('/etc/xrayfa-vm-id').read_text().strip() != args.instance or
            Path('/sys/class/dmi/id/product_uuid').read_text().strip().lower() != args.instance.lower() or
            subprocess.check_output(['systemd-detect-virt', '--vm'], text=True).strip() not in ('kvm', 'qemu')):
        raise RuntimeError('Dedicated root VM identity required')
    journal = Path('/run/xrayfa/session.json')
    if journal.exists():
        raise RuntimeError('Existing TUN journal preserved; recover before testing')
    args.out.mkdir(mode=0o700, parents=True, exist_ok=False)
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ('http_proxy', 'https_proxy', 'all_proxy', 'no_proxy')}
    profile = Path(tempfile.mkdtemp(prefix='xrayfa-tun-smoke-', dir='/home/xfa'))
    os.chown(profile, 1000, 1000)
    env.update(SUDO_UID='1000', XRAYFA_HOME=str(profile), LC_ALL='C')
    cli, core = str(args.cli.resolve()), str(args.core.resolve())

    def command(argv, timeout=30, **kwargs):
        return subprocess.run(argv, env=env, capture_output=True, text=True,
                              timeout=timeout, **kwargs)

    def normalize(value):
        if isinstance(value, dict):
            return {k: normalize(v) for k, v in value.items()
                    if k not in ('valid_life_time', 'preferred_life_time', 'expires', 'packets', 'bytes', 'metainfo')}
        if isinstance(value, list):
            return [normalize(v) for v in value
                    if not isinstance(v, dict) or 'metainfo' not in v]
        return value

    def snapshot():
        commands = {
            'addresses': ['ip', '-j', 'address'], 'links': ['ip', '-j', 'link'],
            'routes4': ['ip', '-j', '-4', 'route', 'show', 'table', 'all'],
            'routes6': ['ip', '-j', '-6', 'route', 'show', 'table', 'all'],
            'rules': ['ip', '-j', 'rule'], 'nft': ['nft', '-j', 'list', 'ruleset'],
            'dns': ['resolvectl', 'dns'], 'domains': ['resolvectl', 'domain'],
            'defaultRoute': ['resolvectl', 'default-route'],
        }
        result = {}
        for name, argv in commands.items():
            output = command(argv)
            assert output.returncode == 0, 'Snapshot failed: ' + name
            try:
                result[name] = normalize(json.loads(output.stdout))
            except json.JSONDecodeError:
                result[name] = output.stdout.strip()
        return result

    imported = command([cli, 'add', str(args.link_file)], user=1000, group=1000, extra_groups=[])
    assert imported.returncode == 0, 'Private node import failed'
    baseline = snapshot()
    (args.out / 'baseline.json').write_text(json.dumps(baseline, indent=2))
    report = {'cliSha256': hashlib.sha256(args.cli.read_bytes()).hexdigest(),
              'coreSha256': hashlib.sha256(args.core.read_bytes()).hexdigest(),
              'trafficSkipped': args.skip_traffic, 'cases': []}

    def save():
        temporary = args.out / 'result.tmp'
        temporary.write_text(json.dumps(report, indent=2) + '\n')
        temporary.replace(args.out / 'result.json')

    def request(socks=False):
        argv = ['curl', '-q', '--max-time', str(args.request_timeout), '--silent', '--show-error', '-o', '/dev/null', '-w', '%{http_code}']
        argv += ['--proxy', 'socks5h://127.0.0.1:10808', '--noproxy', ''] if socks else ['--noproxy', '*']
        argv += ['https://www.google.com/generate_204']
        output = command(argv, timeout=args.request_timeout + 5)
        return {'exit': output.returncode, 'http': output.stdout.strip()}

    with socket.socket() as occupied:
        occupied.bind(('127.0.0.1', 10808))
        occupied.listen()
        conflict = command([cli, 'run', '--tun', '--core', core])
        report['occupiedPort'] = {'exit': conflict.returncode,
                                  'baselineRestored': snapshot() == baseline,
                                  'journalAbsent': not journal.exists()}
        save()
        assert conflict.returncode == 8 and snapshot() == baseline and not journal.exists(), 'Occupied port changed network state'

    for action in ('SIGINT', 'SIGTERM', 'SIGHUP', 'stop', 'core-crash', 'controller-kill', 'external-route'):
        case = {'action': action}
        report['cases'].append(case)
        with (args.out / (action + '.log')).open('w') as log:
            # No ID deliberately verifies sole-node selection from the sudo caller's profile.
            process = subprocess.Popen([cli, 'run', '--tun', '--core', core],
                                       env=env, stdout=log, stderr=log)
            identity = None
            foreign_route = False
            try:
                deadline = time.monotonic() + 25
                while process.poll() is None and time.monotonic() < deadline:
                    if 'stage=Running' in (args.out / (action + '.log')).read_text():
                        identity = json.loads(journal.read_text())
                        break
                    time.sleep(.05)
                case['running'] = identity is not None
                assert case['running'], 'TUN did not reach Running; inspect private case log'
                if action == 'SIGINT':
                    if not args.skip_traffic:
                        case['direct'] = request()
                        case['socks'] = request(socks=True)
                        case['dns'] = command(['getent', 'ahostsv4', 'www.cloudflare.com']).returncode
                    duplicate = command([cli, 'run', '1', '--tun', '--core', core])
                    case['duplicateExit'] = duplicate.returncode
                    assert duplicate.returncode == 8, 'Duplicate TUN must be rejected'
                    if not args.skip_traffic:
                        for mode in ('direct', 'socks'):
                            result = case[mode]
                            assert result['exit'] == 0 and result['http'] == '204', mode + ' request failed'
                        assert case['dns'] == 0, 'System DNS failed'
                    case['activeRecoverExit'] = command([cli, 'recover']).returncode
                    assert case['activeRecoverExit'] == 8, 'Active recovery must be refused'
                    if args.soak_seconds:
                        monitor_tun_session(args, case, process, args.out / (action + '.log'), identity, save)
                if action == 'stop':
                    case['stopExit'] = command([cli, 'stop']).returncode
                    assert case['stopExit'] == 0, 'stop failed'
                elif action == 'core-crash':
                    os.kill(identity['corePid'], signal.SIGKILL)
                elif action == 'controller-kill':
                    process.kill()
                elif action == 'external-route':
                    fault = command(['ip', 'route', 'add', 'blackhole', '203.0.113.77/32', 'metric', '456'])
                    assert fault.returncode == 0, 'Could not inject foreign route'
                    foreign_route = True
                else:
                    process.send_signal(getattr(signal, action))
                case['exit'] = process.wait(timeout=30)
                expected = {'SIGINT': 0, 'SIGTERM': 0, 'SIGHUP': 0, 'stop': 0,
                            'core-crash': 7, 'controller-kill': -9, 'external-route': 9}[action]
                if foreign_route:
                    case['journalRetained'] = journal.exists()
                    retained = command(['ip', '-j', 'route', 'show', '203.0.113.77/32'])
                    case['foreignRoutePreserved'] = bool(json.loads(retained.stdout))
                    assert case['journalRetained'] and case['foreignRoutePreserved'], 'Foreign state must be preserved'
                    assert command(['ip', 'route', 'del', 'blackhole', '203.0.113.77/32', 'metric', '456']).returncode == 0
                    foreign_route = False
                case['recoverExit'] = command([cli, 'recover']).returncode
                case['baselineRestored'] = snapshot() == baseline
                case['journalRemoved'] = not journal.exists()
                with socket.socket() as released:
                    released.bind(('127.0.0.1', 10808))
                assert case['exit'] == expected, 'Unexpected session exit'
                assert case['recoverExit'] == 0 and case['baselineRestored'] and case['journalRemoved'], 'Recovery incomplete'
                case['passed'] = True
            finally:
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=30)
                if foreign_route:
                    command(['ip', 'route', 'del', 'blackhole', '203.0.113.77/32', 'metric', '456'])
                if journal.exists():
                    case['finalRecoveryExit'] = command([cli, 'recover']).returncode
                save()
        print(json.dumps(case), flush=True)


if __name__ == '__main__':
    main()
