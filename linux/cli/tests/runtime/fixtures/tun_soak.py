"""Guest-only monitored TUN soak. Operator inputs and evidence stay private."""
import ipaddress
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time
import uuid


def monitor_tun_session(args, case, process, runtime_log, journal, save):
    """Monitor continuously while running direct HTTPS rounds and metric transitions."""
    if os.geteuid() != 0 or Path('/etc/xrayfa-vm-id').read_text().strip() != args.instance:
        raise RuntimeError('Dedicated root VM required for soak')
    folder = args.out / 'soak'
    folder.mkdir(mode=0o700)
    runner = args.web_runner.resolve()
    if not runner.is_file():
        raise RuntimeError('Provide the website acceptance runner with --web-runner')
    config = json.loads(Path(journal['configPath']).read_text())
    outbound = config['outbounds'][0]
    settings = outbound['settings']
    protocol = outbound['protocol']
    if protocol in ('vless', 'vmess'):
        endpoint = settings['vnext'][0]
    elif protocol == 'hysteria':
        endpoint = settings
    else:
        endpoint = settings['servers'][0]
    node_ip = str(ipaddress.IPv4Address(endpoint['address']))
    node_port = int(endpoint['port'])
    if not 1 <= node_port <= 65535:
        raise RuntimeError('Invalid upstream port')
    underlay = journal['underlay']
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,15}', underlay):
        raise RuntimeError('Unexpected underlay name')
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ('http_proxy', 'https_proxy', 'all_proxy', 'no_proxy')}

    def command(argv, timeout=10):
        return subprocess.run(argv, capture_output=True, text=True, timeout=timeout, env=env)

    def default_route():
        rows = json.loads(command(['ip', '-j', '-4', 'route', 'show', 'default']).stdout)
        if len(rows) != 1 or rows[0].get('dev') != underlay:
            raise RuntimeError('Underlay default changed during the fixture')
        return rows[0]

    original = default_route()
    can_toggle = original.get('protocol') == 'dhcp' and 'metric' in original
    metric = original.get('metric')

    def change_metric(wanted):
        current = default_route()
        if current.get('gateway') != original.get('gateway') or current.get('protocol') != 'dhcp':
            raise RuntimeError('Cannot alter a foreign underlay route')
        if current['metric'] not in (metric, metric + 20000):
            raise RuntimeError('Cannot overwrite an unexpected metric')
        if current['metric'] == wanted:
            return
        gateway = str(ipaddress.IPv4Address(original['gateway']))
        text = f'route del default via {gateway} dev {underlay} metric {current["metric"]}\n'
        text += f'route add default via {gateway} dev {underlay} proto dhcp'
        if original.get('prefsrc'):
            text += ' src ' + str(ipaddress.IPv4Address(original['prefsrc']))
        text += f' metric {wanted}\n'
        path = folder / 'metric.batch'
        path.write_text(text)
        # Deliberately expose a short replacement gap so the route observer is
        # exercised, rather than depending on hitting two adjacent netlink calls.
        commands = text.splitlines()
        result = command(['ip', *commands[0].split()])
        if result.returncode:
            raise RuntimeError('Metric fixture delete failed')
        time.sleep(.1)
        result = command(['ip', *commands[1].split()])
        if result.returncode:
            raise RuntimeError('Metric fixture failed')

    table = 'soak_xfa_' + uuid.uuid4().hex[:10]
    rules = f'''table inet {table} {{
 chain output {{
  type filter hook output priority -5; policy accept;
  tcp dport {{ 10808, 10999 }} counter reject
  oifname "{underlay}" ip daddr {node_ip} tcp dport {node_port} counter accept
  oifname "{underlay}" tcp dport 443 counter reject
 }}
}}
'''
    rules_path = folder / 'isolation.nft'
    rules_path.write_text(rules)
    applied = command(['nft', '-f', str(rules_path)])
    if applied.returncode:
        raise RuntimeError('Could not install proxy/direct-HTTPS isolation')
    evidence = {'requestedSeconds': args.soak_seconds, 'elapsedSeconds': 0,
                'startedUtc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                'proxyPortsBlocked': True, 'physicalDirectHttpsBlocked': True,
                'logAnomalies': [], 'rounds': [], 'metricEvents': [], 'passed': False}
    case['soak'] = evidence
    captures = []
    round_process = None
    round_log = None
    offset = 0
    pending = ''
    start = time.monotonic()
    next_round = start
    next_checkpoint = start
    deadline = start + args.soak_seconds
    schedule = [(fraction * args.soak_seconds, metric + (20000 if index % 2 == 0 else 0))
                for index, fraction in enumerate((.2, .5, .8, .93))] if can_toggle else []
    round_folder = None

    def inspect_log():
        nonlocal offset, pending
        with runtime_log.open(errors='replace') as stream:
            stream.seek(offset)
            pending += stream.read()
            offset = stream.tell()
        lines = pending.split('\n')
        pending = lines.pop()
        for line in lines:
            if not re.search(r'\b(?:ERROR|WARNING) (?:CLI|CORE-OUT|CORE-ERR)\b', line):
                continue
            if 'WARNING CORE-' in line and 'core: Xray ' in line and ' started' in line:
                continue
            evidence['logAnomalies'].append(line)
        if evidence['logAnomalies']:
            save()
            raise RuntimeError('Unexpected runtime log entry; inspect the private soak evidence')

    try:
        for interface, name, packet_filter in [
            ('xrayfa0', 'tun', ['tcp', 'port', '443']),
            (underlay, 'underlay', ['tcp', 'and', '(', 'host', node_ip, 'or', 'port', '443', 'or', 'port', '10808', 'or', 'port', '10999', ')']),
        ]:
            output = (folder / (name + '-capture.log')).open('w')
            capture = subprocess.Popen(['tcpdump', '-U', '-i', interface, '-nn', '-s', '128',
                                        '-w', str(folder / (name + '.pcap')), *packet_filter],
                                       stdout=output, stderr=output)
            captures.append((capture, output))
        while time.monotonic() < deadline or round_process is not None:
            now = time.monotonic()
            evidence['elapsedSeconds'] = round(now - start, 3)
            if process.poll() is not None:
                raise RuntimeError('TUN exited during the soak')
            inspect_log()
            for capture, _ in captures:
                if capture.poll() is not None:
                    raise RuntimeError('Packet capture stopped during the soak')
            if schedule and now - start >= schedule[0][0]:
                _, wanted = schedule.pop(0)
                change_metric(wanted)
                evidence['metricEvents'].append({'elapsedSeconds': round(now - start, 3), 'metric': wanted})
                save()
            if round_process is not None and round_process.poll() is not None:
                code = round_process.returncode
                round_log.close()
                result = json.loads((round_folder / 'report.json').read_text())
                evidence['rounds'].append({'round': len(evidence['rounds']) + 1,
                                           'passed': result['passed'],
                                           'passedRequests': result['passedRequests'],
                                           'totalRequests': result['totalRequests']})
                round_process = None
                save()
                if code != 0 or not result['passed']:
                    raise RuntimeError('Website request failed; inspect the private round report')
            if round_process is None and now < deadline and now >= next_round:
                round_folder = folder / f'round-{len(evidence["rounds"]) + 1:03d}'
                round_log = round_folder.with_suffix('.log').open('w')
                round_process = subprocess.Popen(['python3', str(runner), '--direct', '--rounds', '1',
                                                   '--timeout', str(args.request_timeout), '--out', str(round_folder)],
                                                  stdout=round_log, stderr=round_log, env=env)
                next_round = now + args.soak_interval
            if now >= next_checkpoint:
                save()
                next_checkpoint = now + 5
            time.sleep(.25)
        inspect_log()
        evidence['elapsedSeconds'] = round(time.monotonic() - start, 3)
        if evidence['elapsedSeconds'] < args.soak_seconds or not evidence['rounds']:
            raise RuntimeError('Soak duration or traffic coverage incomplete')
    finally:
        if round_process is not None:
            if round_process.poll() is None:
                round_process.terminate()
            round_process.wait(timeout=args.request_timeout + 10)
            round_log.close()
        for capture, output in captures:
            if capture.poll() is None:
                capture.send_signal(signal.SIGINT)
            capture.wait(timeout=10)
            output.close()
        if can_toggle:
            change_metric(metric)
        removed = command(['nft', 'delete', 'table', 'inet', table])
        evidence['isolationRemoved'] = removed.returncode == 0
        save()
        if not evidence['isolationRemoved']:
            raise RuntimeError('Could not remove fixture isolation rules')
    address = json.loads(command(['ip', '-j', '-4', 'address', 'show', 'dev', underlay]).stdout)[0]['addr_info'][0]['local']

    def packets(name, packet_filter):
        result = command(['tcpdump', '-nn', '-r', str(folder / (name + '.pcap')), *packet_filter])
        if result.returncode:
            raise RuntimeError('Packet evidence could not be decoded')
        return len(result.stdout.splitlines())

    evidence['tunHttpsPackets'] = packets('tun', ['tcp', 'port', '443'])
    evidence['nodeOutboundPackets'] = packets('underlay', ['src', 'host', address, 'and', 'dst', 'host', node_ip, 'and', 'tcp', 'dst', 'port', str(node_port)])
    evidence['proxyOutboundPackets'] = packets('underlay', ['src', 'host', address, 'and', 'tcp', 'and', '(', 'port', '10808', 'or', 'port', '10999', ')'])
    evidence['directHttpsOutboundPackets'] = packets('underlay', ['src', 'host', address, 'and', 'not', 'dst', 'host', node_ip, 'and', 'tcp', 'dst', 'port', '443'])
    evidence['passed'] = (evidence['tunHttpsPackets'] > 0 and evidence['nodeOutboundPackets'] > 0
                          and evidence['proxyOutboundPackets'] == 0 and evidence['directHttpsOutboundPackets'] == 0)
    save()
    if not evidence['passed']:
        raise RuntimeError('TUN path isolation evidence did not pass')
