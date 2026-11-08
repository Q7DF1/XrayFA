#!/usr/bin/env python3
"""Prepare and collect the same-overlay VM reboot obligation."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

import probe
import setup

STATE = setup.STATE
CLI = probe.CLI


def boot_id():
    return Path('/proc/sys/kernel/random/boot_id').read_text().strip()


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('--instance', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--phase', choices=['prepare', 'collect'], required=True)
    args = parser.parse_args()
    setup.guard(args.instance)
    out = args.out.resolve()
    if not out.is_relative_to(STATE / 'scenarios'):
        raise RuntimeError('Private fixture output required')
    out.mkdir(parents=True, exist_ok=True, mode=0o700)
    journal = Path('/run/xrayfa/session.json')
    if args.phase == 'prepare':
        if journal.exists():
            raise RuntimeError('Product journal must be resolved before reboot')
        state = json.loads((STATE / 'state.json').read_text())
        before = probe.snapshot()
        (out / 'prepare.json').write_text(json.dumps({
            'oldBoot': boot_id(), 'fixtureBoot': state['boot'],
            'fixtureBaseline': state['baseline'], 'before': before,
            'instance': args.instance
        }, indent=2) + '\n')
        # Detach reboot from the SSH command. The host runner waits for the
        # verified guest to return with a different boot ID.
        subprocess.Popen(['/usr/bin/systemctl', 'reboot'],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                         start_new_session=True)
        print(json.dumps({'phase': 'prepare', 'boot': boot_id(), 'status': 'reboot-requested'}), flush=True)
        return

    prep = json.loads((out / 'prepare.json').read_text())
    current_boot = boot_id()
    if current_boot == prep['oldBoot']:
        raise RuntimeError('VM boot ID did not change')
    if journal.exists():
        raise RuntimeError('A product journal survived reboot unexpectedly')
    after_reboot = probe.snapshot()
    links = json.loads(after_reboot['links']['stdout']) if after_reboot['links']['exit'] == 0 else []
    routes = json.loads(after_reboot['routes']['stdout']) if after_reboot['routes']['exit'] == 0 else []
    resolver = after_reboot['domains']['stdout'] if after_reboot['domains']['exit'] == 0 else ''
    no_product_network = not any(x.get('ifname') == 'xrayfa0' for x in links) and \
        not any(x.get('dst') in ['0.0.0.0/1', '128.0.0.0/1'] for x in routes) and \
        '198.18.0.2' not in after_reboot['dns']['stdout'] and '~.' not in resolver
    (out / 'after-reboot.json').write_text(json.dumps({
        'boot': current_boot, 'snapshot': after_reboot,
        'noProductNetwork': no_product_network
    }, indent=2) + '\n')
    if not no_product_network:
        raise RuntimeError('Product network state survived reboot')

    # Recreate only the disposable fixture, preserving the reboot evidence and
    # replacing stale process identities from the previous boot.
    old_state = STATE / 'state.json'
    if old_state.exists():
        old_state.rename(STATE / 'state-before-reboot.json')
    for name in ['server-all.json', 'seeds.json']:
        path = STATE / name
        if path.exists():
            path.rename(STATE / (name + '.before-reboot'))
    setup_script = Path(__file__).with_name('setup.py')
    configure_script = Path(__file__).with_name('configure.py')
    subprocess.run(['python3', str(setup_script), 'setup', '--instance', args.instance],
                   check=True, capture_output=True, text=True)
    subprocess.run(['python3', str(configure_script), '--instance', args.instance],
                   check=True, capture_output=True, text=True)
    state = json.loads((STATE / 'state.json').read_text())
    fixture_ready = state['boot'] == current_boot and (STATE / 'server-all.json').exists() and \
        (STATE / 'seeds.json').exists()
    record = {
        'id': 'BOOT-01/reboot', 'expectedExit': 0,
        'command': {'argv': ['vm-reboot'], 'exit': 0},
        'oldBoot': prep['oldBoot'], 'newBoot': current_boot,
        'bootChanged': True, 'noProductNetwork': no_product_network,
        'fixtureReinitialized': fixture_ready,
        'baselineEvidence': str(out / 'after-reboot.json'),
        'status': 'passed' if fixture_ready else 'failed'
    }
    result = {
        'schemaVersion': 1, 'instance': args.instance, 'boot': current_boot,
        'cliSha256': probe.sha(CLI), 'coreSha256': probe.sha(probe.CORE),
        'fixtureSha256': probe.sha(__file__), 'cases': [record],
        'status': record['status']
    }
    (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'id': record['id'], 'status': record['status']}), flush=True)
    raise SystemExit(record['status'] != 'passed')


if __name__ == '__main__':
    main()
