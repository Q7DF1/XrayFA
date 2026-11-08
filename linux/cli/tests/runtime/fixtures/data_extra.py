#!/usr/bin/env python3
"""Exercise the five DATA-02 readiness/data-path failure variants in the VM."""
import argparse
import copy
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time

import probe
import setup

CLI = probe.CLI
CORE = probe.CORE
STATE = setup.STATE


def write_link(path, link):
    path.write_text(link + '\n')
    os.chown(path, 1000, 1000)
    path.chmod(0o600)


def stop_process(record):
    if not record:
        return
    current = probe.pid_record(record['pid'])
    if current == record:
        os.kill(record['pid'], signal.SIGTERM)
        for _ in range(100):
            if probe.pid_record(record['pid']) is None:
                return
            time.sleep(.02)
        if probe.pid_record(record['pid']) == record:
            os.kill(record['pid'], signal.SIGKILL)


def start_variant_server(path, cert=None, port=24910, omit_dns=False):
    source = json.loads((STATE / 'server-all.json').read_text())
    inbound = next(x for x in source['inbounds'] if x.get('tag') == 'RT-VLESS-XHTTP')
    inbound = copy.deepcopy(inbound)
    inbound['tag'] = 'DATA-02-variant'
    inbound['port'] = port
    if cert:
        inbound['streamSettings']['tlsSettings']['certificates'] = [{
            'certificateFile': str(cert[0]), 'keyFile': str(cert[1])
        }]
    config = {
        'log': {'loglevel': 'warning'},
        'inbounds': [inbound],
        'outbounds': copy.deepcopy(source['outbounds']),
        'routing': copy.deepcopy(source['routing']),
    }
    if omit_dns:
        config['routing']['rules'] = [
            x for x in config['routing']['rules']
            if not (x.get('port') == '53')
        ]
    path.write_text(json.dumps(config, indent=2) + '\n')
    setup.run([CORE, 'run', '-test', '-c', str(path)], stdout=subprocess.DEVNULL)
    return setup.spawn([CORE, 'run', '-c', str(path)], path.name + '.log')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('--instance', required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    setup.guard(args.instance)
    out = args.out.resolve()
    if not out.is_relative_to(STATE / 'scenarios'):
        raise RuntimeError('Private scenario path required')
    out.mkdir(parents=True, exist_ok=False, mode=0o700)
    if Path('/run/xrayfa/session.json').exists():
        raise RuntimeError('Existing product journal must be resolved first')
    fixture_state = json.loads((STATE / 'state.json').read_text())
    boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
    if fixture_state['boot'] != boot:
        raise RuntimeError('Fixture state boot mismatch')
    nodes = json.loads(Path('/home/xfa/.local/share/xrayfa/nodes.json').read_text())['nodes']
    base = next(n['url'] for n in nodes if '10.204.0.200:24402?' in n['url'])
    report = {
        'schemaVersion': 1, 'instance': args.instance, 'boot': boot,
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': []
    }

    variants = [
        ('DATA-02/unreachable-proxy', base.replace('10.204.0.200:24402', '10.204.0.201:24402'), None),
        ('DATA-02/bad-auth', re.sub(r'vless://[^@]+@', 'vless://aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa@', base), None),
        ('DATA-02/bad-sni', base.replace('sni=proxy.xfa.test', 'sni=bad-sni.xfa.test').replace('host=proxy.xfa.test', 'host=bad-sni.xfa.test'), None),
        ('DATA-02/bad-certificate', base, 'bad-cert'),
        ('DATA-02/dns-unreachable', base, 'no-dns'),
    ]

    def save():
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

    for case_id, link, special in variants:
        folder = out / case_id.replace('/', '-')
        folder.mkdir(mode=0o700)
        case = {'id': case_id, 'status': 'failed', 'expectedExit': 7,
                'before': probe.snapshot(), 'evidenceDirectory': folder.name}
        capture = None
        variant_process = None
        try:
            if Path('/run/xrayfa/session.json').exists():
                raise RuntimeError('Product journal exists before isolated case')
            if special == 'bad-cert':
                cert = STATE / 'bad-server.crt'; key = STATE / 'bad-server.key'
                setup.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
                           '-keyout', str(key), '-out', str(cert), '-days', '3',
                           '-subj', '/CN=proxy.xfa.test'], stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL)
                # The certificate is intentionally self-signed; the client still uses
                # the fixture CA pool and must reject this endpoint.
                variant_path = STATE / 'data-bad-cert.json'
                variant_process = start_variant_server(variant_path, (cert, key), 24910)
                link = base.replace('10.204.0.200:24402', '10.204.0.200:24910')
            elif special == 'no-dns':
                variant_path = STATE / 'data-no-dns.json'
                variant_process = start_variant_server(variant_path, None, 24911, omit_dns=True)
                link = base.replace('10.204.0.200:24402', '10.204.0.200:24911')

            link_file = folder / 'link.txt'
            write_link(link_file, link)
            user_link = Path('/home/xfa/.data-extra-link')
            write_link(user_link, link)
            try:
                added = probe.cmd([CLI, 'add', '--link-file', str(user_link)], user=True)
            finally:
                user_link.unlink(missing_ok=True)
            case['add'] = added
            if added['exit'] != 0:
                raise RuntimeError('Synthetic failure link was rejected')
            case['nodeId'] = int(re.search(r'Added node (\d+)', added['stdout'])[1])
            case['linkSha256'] = probe.sha(link_file)
            capture_path = folder / 'underlay.pcap'
            capture = subprocess.Popen(['tcpdump', '-U', '-nn', '-i', 'xfa-underlay',
                                        '-w', str(capture_path)],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            command = probe.cmd([CLI, 'run', str(case['nodeId']), '--core', CORE,
                                 '--log-level', 'debug'], timeout=30)
            case['command'] = command
            case['terminationSignal'] = None
            if command['exit'] != 7:
                raise RuntimeError(f"Expected exit 7, observed {command['exit']}")
            case['journalAfterCommand'] = Path('/run/xrayfa/session.json').exists()
            case['after'] = probe.snapshot()
            case['baselineEqual'] = probe.config_snapshot(case['before']) == probe.config_snapshot(case['after'])
            case['noJournal'] = not case['journalAfterCommand']
            if not case['noJournal'] or not case['baselineEqual']:
                raise RuntimeError('Failure did not restore exact baseline')
            case['coreStarted'] = 'stage=Running' in command.get('stdout', '') or 'stage=Running' in command.get('stderr', '')
            case['noDirectFallback'] = True
            case['status'] = 'passed'
        except Exception as exc:
            case['error'] = str(exc)
        finally:
            if capture is not None:
                capture.send_signal(signal.SIGINT)
                try:
                    capture.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    capture.kill(); capture.wait(timeout=2)
                if capture_path.exists():
                    text = probe.cmd(['tcpdump', '-nn', '-tttt', '-r', str(capture_path)], timeout=5)
                    (folder / 'underlay.txt').write_text(text['stdout'] + '\n' + text['stderr'])
                    case['underlay'] = text
                    case['noDirectFallback'] = '203.0.113.10' not in text['stdout']
            stop_process(variant_process)
            if Path('/run/xrayfa/session.json').exists():
                case['finalRecover'] = probe.cmd([CLI, 'recover'], timeout=24)
            case['afterFinally'] = probe.snapshot()
            case['status'] = 'passed' if case['status'] == 'passed' and case.get('noDirectFallback', False) else 'failed'
            report['cases'].append(case)
            save()
            print(json.dumps({'id': case_id, 'status': case['status'], 'error': case.get('error')}), flush=True)
        if Path('/run/xrayfa/session.json').exists():
            raise RuntimeError('Retained journal blocks next DATA-02 case')

    report['status'] = 'passed' if all(c['status'] == 'passed' for c in report['cases']) else 'failed'
    save()
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
