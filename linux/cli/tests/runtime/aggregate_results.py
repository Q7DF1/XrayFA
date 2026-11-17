#!/usr/bin/env python3
"""Aggregate immutable runtime reports without manufacturing missing outcomes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def atomic_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.aggregate-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as f:
            json.dump(value, f, indent=2, ensure_ascii=False)
            f.write('\n')
            f.flush()
            os.fsync(f.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def main():
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest', type=Path, required=True)
    p.add_argument('--report', type=Path, action='append', required=True,
                   help='One immutable run_runtime report.json; repeat for each run')
    p.add_argument('--out', type=Path, required=True)
    args = p.parse_args()

    manifest_path = args.manifest.resolve()
    raw = manifest_path.read_bytes()
    manifest_hash = hashlib.sha256(raw).hexdigest()
    sidecar = manifest_path.with_suffix('.sha256')
    if sidecar.read_text().split()[0] != manifest_hash:
        raise SystemExit('Manifest sidecar digest mismatch')
    manifest = json.loads(raw)
    expected = {c['id']: c for c in manifest['cases']}
    merged = {case_id: {
        'id': case_id, 'category': case['category'],
        'mandatory': case['mandatory'], 'status': 'not-executed'
    } for case_id, case in expected.items()}
    provenance = []
    identity = None
    fixture_hashes = set()
    boots = set()
    duplicate = set()

    for report_path_arg in args.report:
        report_path = report_path_arg.resolve()
        report = json.loads(report_path.read_text())
        if report.get('manifestSha256') != manifest_hash:
            raise SystemExit(f'Manifest hash mismatch: {report_path}')
        run_identity = set()
        run_boots = set()
        for probe in report.get('probes', []):
            run_identity.add((probe.get('cliSha256'), probe.get('coreSha256')))
            if probe.get('fixtureSha256'):
                fixture_hashes.add(probe['fixtureSha256'])
            if probe.get('boot'):
                run_boots.add(probe['boot'])
        for case in report.get('cases', []):
            case_id = case.get('id')
            if case_id not in expected:
                raise SystemExit(f'Case absent from manifest: {case_id}')
            if case.get('status') == 'not-executed':
                continue
            if case_id in duplicate:
                raise SystemExit(f'Duplicate executed case: {case_id}')
            duplicate.add(case_id)
            # Scenario results carry identity on each case; runtime results carry
            # it in the per-seed probe. The report itself must never be accepted
            # as evidence merely because its status says passed.
            identity_fields = [case.get(k) for k in ('cliSha256', 'coreSha256', 'fixtureSha256')]
            if all(identity_fields):
                run_identity.add(tuple(identity_fields[:2]))
                fixture_hashes.add(identity_fields[2])
            merged[case_id] = dict(case)
        provenance.append({'path': str(report_path), 'sha256': digest(report_path),
                           'runId': report.get('runId'), 'status': report.get('status'),
                           'counts': report.get('counts', {})})
        if identity is None:
            identity = run_identity
            boots = run_boots
        elif run_identity and identity != run_identity:
            raise SystemExit(f'Runtime identity mismatch between reports: {report_path}')
        elif run_boots and boots and run_boots != boots:
            raise SystemExit(f'VM boot identity mismatch between reports: {report_path}')
        boots.update(run_boots)

    counts = {}
    for case in merged.values():
        counts[case['status']] = counts.get(case['status'], 0) + 1
    mandatory = [case for case in merged.values() if case['mandatory']]
    result = {
        'schemaVersion': 1,
        'status': 'passed' if all(case['status'] == 'passed' for case in mandatory) else 'incomplete',
        'manifestSha256': manifest_hash,
        'cliCoreIdentities': [list(pair) for pair in sorted(identity or set())],
        'fixtureSha256': sorted(fixture_hashes),
        'bootIds': sorted(boots),
        'sourceReports': provenance,
        'executedCaseCount': len(duplicate),
        'counts': counts,
        'cases': list(merged.values()),
    }
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True, mode=0o700)
    atomic_json(out / 'report.json', result)
    lines = [
        '# Aggregated runtime acceptance', '',
        f'Manifest SHA-256: `{manifest_hash}`',
        f'Source reports: {len(provenance)}',
        f'Executed unique cases: {len(duplicate)}/{len(expected)}',
        f'Status: {result["status"]}', '', '| Status | Count |', '|---|---:|'
    ]
    lines.extend(f'| {name} | {count} |' for name, count in sorted(counts.items()))
    (out / 'report.md').write_text('\n'.join(lines) + '\n')
    print(json.dumps({'status': result['status'], 'counts': counts,
                      'executedCaseCount': len(duplicate)}, ensure_ascii=False))
    raise SystemExit(result['status'] != 'passed')


if __name__ == '__main__':
    main()
