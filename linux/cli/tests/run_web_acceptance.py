#!/usr/bin/env python3
"""Real website acceptance through SOCKS5 or a running TUN session.

No credentials are accepted or response bodies retained. Every individual
request must pass; retries never turn a failed sample into a passed one.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

TARGETS = (
    ('google', 'https://www.google.com/', b'google'),
    ('cloudflare', 'https://www.cloudflare.com/cdn-cgi/trace', b'ip='),
    ('github', 'https://github.com/', b'github'),
    ('wikipedia', 'https://www.wikipedia.org/', b'wikipedia'),
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--proxy', help='For example socks5h://127.0.0.1:10808')
    mode.add_argument('--direct', action='store_true', help='Use system routing; start TUN separately')
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--timeout', type=int, default=30)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if args.rounds < 1 or args.timeout < 1:
        parser.error('rounds and timeout must be positive')
    os.umask(0o077)
    args.out.mkdir(mode=0o700, parents=True, exist_ok=False)
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ('http_proxy', 'https_proxy', 'all_proxy', 'no_proxy')}
    report = {'mode': 'direct' if args.direct else 'socks5', 'rounds': args.rounds,
              'timeoutSeconds': args.timeout, 'requests': [], 'passed': False}

    def request(target):
        name, url, marker = target
        argv = ['curl', '-q', '--silent', '--show-error', '--fail', '--location',
                '--max-redirs', '3', '--connect-timeout', str(args.timeout),
                '--max-time', str(args.timeout), '--write-out', '\n%{json}', url]
        argv += ['--proxy', args.proxy, '--noproxy', ''] if args.proxy else ['--noproxy', '*']
        started = time.monotonic()
        result = subprocess.run(argv, env=env, capture_output=True, timeout=args.timeout + 5)
        body, _, metadata = result.stdout.rpartition(b'\n')
        try:
            measured = json.loads(metadata)
        except (json.JSONDecodeError, UnicodeDecodeError):
            measured = {}
            body = result.stdout
        record = {'site': name, 'url': url, 'curlExit': result.returncode,
                  'http': measured.get('http_code'), 'bytes': len(body),
                  'bodySha256': hashlib.sha256(body).hexdigest(),
                  'contentMatched': marker in body.lower(),
                  'seconds': round(time.monotonic() - started, 3),
                  'tlsSeconds': measured.get('time_appconnect'),
                  'tlsVerifyResult': measured.get('ssl_verify_result'),
                  'error': result.stderr.decode(errors='replace').strip()}
        record['passed'] = (result.returncode == 0 and record['http'] == 200
                            and record['contentMatched'] and record['tlsVerifyResult'] == 0)
        return record

    for iteration in range(1, args.rounds + 1):
        with ThreadPoolExecutor(max_workers=len(TARGETS)) as pool:
            for record in pool.map(request, TARGETS):
                record['round'] = iteration
                report['requests'].append(record)
                print(json.dumps(record), flush=True)
        (args.out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    report['passed'] = all(r['passed'] for r in report['requests'])
    report['passedRequests'] = sum(r['passed'] for r in report['requests'])
    report['totalRequests'] = len(report['requests'])
    (args.out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: report[k] for k in ('passed', 'passedRequests', 'totalRequests')}), flush=True)
    raise SystemExit(0 if report['passed'] else 1)


if __name__ == '__main__':
    main()
