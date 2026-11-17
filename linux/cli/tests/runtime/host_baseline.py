#!/usr/bin/env python3
"""Record the host network audit around the VM-only runtime suite."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


VOLATILE = {'valid_life_time', 'preferred_life_time', 'expires', 'packets', 'bytes', 'metainfo'}
COMMANDS = {
    'addresses': ['ip', '-j', 'address'],
    'routes': ['ip', '-j', 'route', 'show', 'table', 'all'],
    'rules': ['ip', '-j', 'rule'],
    'resolver': ['resolvectl', 'status'],
    'nft': ['nft', '-j', 'list', 'ruleset'],
}


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def normalize(value):
    if isinstance(value, dict):
        return {k: normalize(v) for k, v in value.items() if k not in VOLATILE}
    if isinstance(value, list):
        return [normalize(v) for v in value]
    return value


def command(argv):
    p = subprocess.run(argv, capture_output=True, text=True)
    return {'argv': argv, 'exit': p.returncode, 'stdout': p.stdout, 'stderr': p.stderr}


def comparable(entry):
    try:
        return {'exit': entry['exit'], 'json': normalize(json.loads(entry['stdout'])),
                'stderr': entry['stderr']}
    except (json.JSONDecodeError, TypeError):
        return {'exit': entry['exit'], 'stdout': entry['stdout'], 'stderr': entry['stderr']}


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--before-dir', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--cli', type=Path, required=True)
    p.add_argument('--core-sha256', required=True)
    p.add_argument('--fixture', type=Path, required=True)
    args = p.parse_args()
    args.out.mkdir(parents=True, exist_ok=False, mode=0o700)
    before = {}
    current = {}
    comparisons = {}
    for name, argv in COMMANDS.items():
        before[name] = json.loads((args.before_dir / f'host-before-{list(COMMANDS).index(name)}.json').read_text())
        current[name] = command(argv)
        comparisons[name] = comparable(before[name]) == comparable(current[name])
    cli_hash = digest(args.cli)
    core_hash = args.core_sha256
    fixture_hash = digest(args.fixture)
    case = {
        'id': 'BASE-01/host', 'mandatory': True, 'category': 'scenario',
        'status': 'passed' if all(comparisons.values()) else 'failed',
        'cliSha256': cli_hash, 'coreSha256': core_hash, 'fixtureSha256': fixture_hash,
        'before': before, 'after': current, 'normalizedEqual': comparisons,
        'hostNetworkUnchanged': all(comparisons.values()),
        'vmOnlyClaim': True,
    }
    report = {
        'schemaVersion': 1, 'manifestSha256': hashlib.sha256(
            Path(__file__).resolve().parents[1].joinpath('runtime-manifest.json').read_bytes()).hexdigest(),
        'status': case['status'], 'counts': {case['status']: 1},
        'cases': [case], 'hostAudit': 'read-only host commands; product ran only in private VM',
    }
    (args.out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    (args.out / 'report.md').write_text('# Host baseline\n\n- BASE-01/host: ' + case['status'] + '\n')
    print(json.dumps({'status': report['status'], 'comparisons': comparisons}))
    raise SystemExit(report['status'] != 'passed')


if __name__ == '__main__':
    main()
