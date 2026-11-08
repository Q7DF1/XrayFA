#!/usr/bin/env python3
"""Freeze runtime obligations before execution; never manufacture test outcomes."""
import hashlib
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEEDS = [
    ('VLESS-TLS', 'vless', 'raw', 'tls', 'xtls-rprx-vision'),
    ('VLESS-XHTTP', 'vless', 'xhttp', 'tls', 'auto'),
    ('VLESS-REALITY', 'vless', 'raw', 'reality', None),
    ('VMESS', 'vmess', 'raw', 'none', 'auto'),
    ('TROJAN', 'trojan', 'raw', 'tls', None),
]
CIPHERS = ['aes-128-gcm', 'aes-256-gcm', 'chacha20-ietf-poly1305',
           'xchacha20-ietf-poly1305', '2022-blake3-aes-128-gcm',
           '2022-blake3-aes-256-gcm', '2022-blake3-chacha20-poly1305']
SEEDS += [(f'SS-{i:02}', 'shadowsocks', 'raw', 'none', c) for i, c in enumerate(CIPHERS, 1)]
SEEDS += [(f'HY2-{i:02}', 'hysteria', 'quic', 'tls', c) for i, c in enumerate(['none', 'salamander'], 1)]
SEEDS += [(f'{p.upper()}-{i:02}', p, 'raw', 'none', c)
          for p in ['socks', 'http'] for i, c in enumerate(['none', 'password'], 1)]
# Each item is an independently executable obligation, including negative cases.
SCENARIOS = {
'DIAG-01': ['user-text', 'user-json', 'root-text', 'root-json'],
'AUTH-01': ['unprivileged'], 'AUTH-02': ['sudo', 'root', 'forged-home', 'forged-xrayfa-home'],
'STORE-01': ['missing', 'corrupt', 'owner', 'permissions', 'symlink'],
'PRE-01': ['missing-core', 'nonexec-core', 'test-failure', 'config-replaced', 'core-replaced'],
'PRE-02': ['tun', 'capability', 'tool', 'resolver'],
'PRE-03': ['active-session', 'interface', 'address', 'vpn', 'dns', 'route'],
'PROC-01': ['exec', 'readiness-timeout', 'early-exit', 'nonzero-exit', 'zero-exit'],
'PROC-02': ['ignore-term', 'held-output'],
'DATA-02': ['unreachable-proxy', 'bad-auth', 'bad-sni', 'bad-certificate', 'dns-unreachable'],
'LOOP-01': ['nonconnected-proxy'], 'DNS-01': ['stub', 'system', 'cache-miss', 'new-domain', 'udp', 'tcp'],
'DNS-02': ['partial-apply', 'restore-failure'], 'IPV6-01': ['canary-three-states'],
'EXT-01': ['nft-deleted', 'interface-deleted', 'dns-changed', 'route-changed'],
'EXIT-01': ['sigint', 'sigterm', 'sighup', 'repeat-cleanup'],
'CRASH-01': ['journal', 'gate', 'tun-created', 'dns-set', 'cleanup'],
'CRASH-02': ['core-kill', 'both-kill', 'live-orphan'],
'REC-01': ['active', 'pid-reuse', 'wrong-exe', 'foreign-interface', 'corrupt-journal', 'cross-boot'],
'REC-02': ['first', 'second', 'retry-after-failure'], 'BOOT-01': ['reboot'],
'LOG-01': ['realtime', 'persist', 'tail', 'follow', 'levels', 'redaction', 'idle-70s', 'two-rotations'],
'LOG-02': ['flood', 'blocked-writer', 'full', 'write-failure', 'simultaneous-core-failure'],
'LEASE-01': ['write', 'fsync', 'partial-intent', 'duplicate'],
'BASE-01': ['each-case', 'suite', 'host'],
}

def expectation(prefix, variant):
    code, stage, effects, retained = 0, 'completed', 'none', []
    if prefix == 'AUTH-01': code, stage = 4, 'identity'
    elif prefix == 'STORE-01': code, stage = (3 if variant == 'missing' else 10), 'snapshot'
    elif prefix == 'PRE-01':
        code = {'missing-core':5, 'nonexec-core':4, 'test-failure':6, 'config-replaced':6, 'core-replaced':5}[variant]
        stage = 'validation'
    elif prefix == 'PRE-02': code, stage = 5, 'preflight'
    elif prefix == 'PRE-03':
        code, stage = (8 if variant in ['active-session','interface','address'] else 5), 'preflight'
    elif prefix in ['PROC-01','PROC-02']: code, stage, effects = (0 if prefix=='PROC-02' and variant=='ignore-term' else 7), 'runtime', 'owned-partial'
    elif prefix == 'DNS-02':
        code, stage, effects = (7 if variant == 'partial-apply' else 9), 'resolver', 'owned-partial'
        if code == 9: retained = ['journal','unrestored-resolver-state']
    elif prefix == 'EXT-01':
        code, stage, effects = (7 if variant in ['nft-deleted','interface-deleted'] else 9), 'supervision', 'owned-and-external'
        if code == 9: retained = ['journal','external-after-image']
    elif prefix.startswith('CRASH-'):
        code, stage, effects = (7 if prefix == 'CRASH-02' and variant == 'core-kill' else None), 'crash', 'owned-partial'
    elif prefix == 'REC-01':
        code, stage, retained = (8 if variant == 'active' else 9), 'recovery', ['journal','foreign-resources-and-processes']
    elif prefix == 'LOG-02': code, stage, effects = (0 if variant == 'flood' else 11), 'logging', 'owned-partial'
    elif prefix == 'LEASE-01': code, stage = (8 if variant == 'duplicate' else 9 if variant=='partial-intent' else 10), ('recovery' if variant=='partial-intent' else 'lease')
    elif prefix == 'DATA-02': code, stage, effects = 7, 'readiness', 'owned-partial'
    elif prefix in ['EXIT-01','REC-02','BOOT-01','LOOP-01','DNS-01','IPV6-01','LOG-01','BASE-01']:
        effects = 'owned-restored'
    request = 'fails-without-direct-fallback' if prefix == 'DATA-02' else 'per-case-assertions'
    return dict(cliExit=code, terminationSignal=9 if prefix.startswith('CRASH-') and code is None else None, stage=stage, allowedEffects=effects, retained=retained,
                coreStarted=stage in ['runtime','resolver','supervision','crash','logging'],
                request=request, recoveryExit=0 if prefix.startswith('CRASH-') else None,
                restore='exact-baseline' if code != 9 else 'owned-best-effort-foreign-preserved-journal-retained',
                retryAfterFaultRemoval='exit-0-exact-baseline' if code == 9 else None)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--revise-from');p.add_argument('--reason');args=p.parse_args()
    cases = []
    for seed, protocol, transport, security, variant in SEEDS:
        for request in ['http', 'https', 'system-dns', 'dns-udp', 'dns-tcp', 'udp', 'ipv6-three-states', 'stop-restore', 'bypass-control', 'server-off-control']:
            blocked = request == 'udp' and protocol in ['http', 'socks']
            cases.append(dict(id=f'RT-{seed}/{request}', category='runtime', protocol=protocol,
                shareFormat='native-share-link', transport=transport, security=security, variant=variant,
                udpPolicy='block' if protocol in ['http', 'socks'] else 'proxy',
                fixture='isolated-netns-v1', managedPlan='ipv4-tun-resolved-nft-v1',
                request=request, nonce='unique-per-execution', mandatory=True,
                expected=dict(stage='Running', request='blocked-with-evidence' if blocked else 'assertion-pass',
                              cliExit=0, coreExit='controlled-stop', restore='exact-case-baseline'),
                fault=None, basis='isolated VM protocol data path and baseline restoration'))
    for prefix, variants in SCENARIOS.items():
        for variant in variants:
            cases.append(dict(id=f'{prefix}/{variant}', category='scenario', protocol=None,
                shareFormat=None, transport=None, security=None, variant=variant, udpPolicy=None,
                fixture='isolated-netns-v1', managedPlan='ipv4-tun-resolved-nft-v1', request=variant,
                nonce='unique-per-execution', mandatory=True, fault=variant,
                expected=expectation(prefix, variant),
                basis='isolated VM lifecycle and fault recovery'))
    manifest = dict(schemaVersion=2, status='frozen-obligations; execution results are separate',
        coreSha256='cc4b84d3069569f297a61f04b914f2bb37e9d202fd7b84cb50def1fc30fd40e1',
        target='Ubuntu 22.04 amd64', seedCount=len(SEEDS),
        timeoutsMs=dict(version=5000, validation=30000, readiness=10000, term=3000,
                        kill=2000, tool=5000, cleanup=20000, logFlush=2000),
        evidenceRequired=['vm-identity', 'binary-hashes', 'final-config-hash', 'cli-exit', 'core-exit',
                          'request-result', 'tun', 'underlay', 'server-nonce', 'restore-items', 'logs'],
        cases=cases)
    target = ROOT / 'runtime-manifest.json'
    payload = (json.dumps(manifest, indent=2, ensure_ascii=False)+'\n').encode()
    if target.exists() and target.read_bytes() != payload:
        old=target.read_bytes();oldHash=hashlib.sha256(old).hexdigest()
        if args.revise_from!=oldHash or not args.reason:
            raise SystemExit('Refusing silent revision; --revise-from SHA256 --reason TEXT required')
        history=ROOT/'runtime/history';history.mkdir(parents=True,exist_ok=True)
        archived=history/(oldHash+'.json')
        if archived.exists() and archived.read_bytes()!=old:raise SystemExit('History artifact conflict')
        archived.write_bytes(old)
        archived.with_suffix('.sha256').write_text(f'{oldHash}  {archived.name}\n')
        revisions=ROOT/'runtime/manifest-revisions.json'
        records=__import__('json').loads(revisions.read_text()) if revisions.exists() else []
        records.append(dict(fromSha256=oldHash,toSha256=hashlib.sha256(payload).hexdigest(),reason=args.reason,
            retainedCaseIds=[c['id'] for c in manifest['cases']]==[c['id'] for c in json.loads(old)['cases']]))
        revisions.write_text(json.dumps(records,indent=2)+'\n')
    target.write_bytes(payload)
    digest = hashlib.sha256(payload).hexdigest()
    target.with_suffix('.sha256').write_text(f'{digest}  {target.name}\n')
    print(json.dumps(dict(cases=len(cases), seeds=len(SEEDS), sha256=digest)))

if __name__ == '__main__':
    main()
