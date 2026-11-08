#!/usr/bin/env python3
"""Map one real TUN session to DNS, loop and IPv6 frozen obligations."""
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
        raise RuntimeError('Existing runtime journal must be resolved first')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    node = next(n['id'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    before = probe.snapshot()
    report = {
        'schemaVersion': 1, 'instance': args.instance,
        'boot': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ['http_proxy', 'https_proxy', 'all_proxy', 'no_proxy']}
    env.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', LANG='C', LC_ALL='C', SUDO_UID='1000')
    process = None
    log_file = (out / 'cli.log').open('wb')
    nonce = uuid.uuid4().hex

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    def curl(url):
        return probe.cmd(['curl', '--noproxy', '*', '--max-time', '4', '--fail',
                          '--silent', '--show-error', url], user=True)

    def dns(label, tcp=False):
        domain = f'{nonce}-{label}.xfa.test'
        return probe.cmd(['dig', *(['+tcp'] if tcp else []), '@198.18.0.2', domain,
                          'A', '+tries=1', '+time=2'], user=True)

    v6_before = curl(f'http://[2001:db8:203::2]:18081/{nonce}/before')

    def case(case_id, command, assertion):
        record = {'id': case_id, 'status': 'passed' if assertion else 'failed',
                  'command': command, 'nonce': nonce}
        report['cases'].append(record)
        save()

    try:
        process = subprocess.Popen([CLI, 'run', str(node), '--core', CORE,
                                    '--log-level', 'debug'], stdout=log_file,
                                   stderr=subprocess.STDOUT, env=env)
        deadline = time.monotonic() + 18
        while time.monotonic() < deadline and process.poll() is None:
            if journal.exists() and json.loads(journal.read_text()).get('phase') == 'Running':
                break
            time.sleep(.05)
        if not journal.exists():
            raise RuntimeError('Real TUN session did not reach Running')

        udp = dns('udp')
        tcp = dns('tcp', tcp=True)
        stub = dns('stub')
        cache_miss = dns('cache-miss')
        new_domain = dns('new-domain')
        system = probe.cmd(['getent', 'ahostsv4', f'{nonce}-system.xfa.test'], user=True)
        dns_results = {
            'udp': udp, 'tcp': tcp, 'stub': stub, 'cache-miss': cache_miss,
            'new-domain': new_domain, 'system': system
        }
        (out / 'dns-results.json').write_text(json.dumps(dns_results, indent=2) + '\n')
        for label, result in dns_results.items():
            case('DNS-01/' + label, result,
                 result['exit'] == 0 and '203.0.113.10' in result['stdout'])

        route = probe.cmd(['ip', '-j', 'route', 'get', '10.204.0.200'])
        http = curl(f'http://203.0.113.10/{nonce}/loop')
        route_json = json.loads(route['stdout'])[0] if route['exit'] == 0 else {}
        running_journal = json.loads(journal.read_text())
        final_config = json.loads(Path(running_journal['configPath']).read_text())
        underlay = final_config['inbounds'][0]['settings'].get('autoOutboundsInterface')
        loop_command = dict(http, route=route_json, autoOutboundsInterface=underlay)
        case('LOOP-01/nonconnected-proxy', loop_command,
             http['exit'] == 0 and nonce in http['stdout'] and underlay == 'xfa-underlay')

        v6_during = curl(f'http://[2001:db8:203::2]:18081/{nonce}/during')
        v6_command = {'argv': ['ipv6-canary'], 'exit': 0,
                      'before': v6_before, 'during': v6_during}
        # The fixture destination is reachable before takeover, blocked by the
        # session's IPv6 policy while Running, and checked again after cleanup.
        v6_assertion = v6_before['exit'] == 0 and v6_during['exit'] != 0
        case('IPV6-01/canary-three-states', v6_command, v6_assertion)
    except Exception as exc:
        report['error'] = str(exc)
    finally:
        if process is not None and process.poll() is None:
            process.send_signal(signal.SIGTERM)
            try:
                process.wait(timeout=24)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=3)
        report['cliExit'] = process.returncode if process is not None else None
        report['recover'] = probe.cmd([CLI, 'recover'], timeout=24)
        report['recoverAgain'] = probe.cmd([CLI, 'recover'], timeout=24)
        report['ipv6After'] = curl(f'http://[2001:db8:203::2]:18081/{nonce}/after')
        report['after'] = probe.snapshot()
        report['baselineEqual'] = probe.config_snapshot(before) == probe.config_snapshot(report['after'])
        report['noJournal'] = not journal.exists()
        for record in report['cases']:
            record['cleanup'] = report['cliExit'] == 0 and report['recover']['exit'] == 0 and \
                report['recoverAgain']['exit'] == 0 and report['baselineEqual'] and report['noJournal']
            if not record['cleanup']:
                record['status'] = 'failed'
        for record in report['cases']:
            if record['id'] == 'IPV6-01/canary-three-states':
                record['command']['after'] = report['ipv6After']
                if report['ipv6After']['exit'] != 0:
                    record['status'] = 'failed'
        if report.get('error'):
            for record in report['cases']:
                record['status'] = 'failed'
        report['status'] = 'passed' if len(report['cases']) == 8 and \
            all(record['status'] == 'passed' for record in report['cases']) else 'failed'
        log_file.close()
        save()
    print(json.dumps({'status': report['status'], 'cases': len(report['cases']),
                      'error': report.get('error')}), flush=True)
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
