"""Isolated file import, JSON composition, clear and automatic startup acceptance."""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import stat
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cli', type=Path, required=True)
    parser.add_argument('--core', type=Path, required=True)
    args = parser.parse_args()
    os.umask(0o077)
    with tempfile.TemporaryDirectory(prefix='xrayfa-workflow-') as raw:
        root = Path(raw)
        cli, core = root / 'xrayfa', root / 'xray'
        shutil.copy2(args.cli, cli)
        shutil.copy2(args.core, core)
        env = {**os.environ, 'XRAYFA_HOME': str(root / 'profile')}

        def run(*argv, code=0):
            result = subprocess.run([str(cli), *map(str, argv)], env=env,
                                    capture_output=True, text=True, timeout=40)
            assert result.returncode == code, (argv[0], result.returncode)
            return result

        # Both outbounds must survive composition; imported listeners are replaced.
        source = {'outbounds': [{'protocol': 'freedom', 'tag': 'direct'},
                                {'protocol': 'blackhole', 'tag': 'blocked'}],
                  'inbounds': [{'protocol': 'socks', 'port': 12345}]}
        node = root / 'node.json'
        node.write_text(json.dumps(source, indent=2))
        run('run', code=2)  # Empty automatic selection.
        run('run', '1', '--tun', '--tun', code=2)
        run('run', '1', '--tun', 'true', code=2)
        if os.geteuid() != 0:
            run('run', '1', '--tun', code=4)
            run('run', '--tun', code=4)
        run('add', node)
        output = root / 'validated.json'
        run('check', '1', '--output', output)  # Adjacent core, even outside its cwd.
        config = json.loads(output.read_text())
        assert config['outbounds'] == source['outbounds']
        assert len(config['inbounds']) == 1
        assert config['inbounds'][0]['listen'] == '127.0.0.1'
        assert config['inbounds'][0]['port'] == 10808
        with (root / 'runtime.log').open('wb') as log:
            process = subprocess.Popen([str(cli), 'run'], env=env, stdout=log, stderr=log)
            try:
                for _ in range(300):
                    assert process.poll() is None, 'Automatic startup failed'
                    try:
                        with socket.create_connection(('127.0.0.1', 10808), .1):
                            break
                    except OSError:
                        time.sleep(.05)
                else:
                    raise AssertionError('Listener timeout')
            finally:
                if process.poll() is None:
                    process.send_signal(signal.SIGTERM)
                assert process.wait(timeout=10) == 0
        run('add', node)
        run('run', code=2)  # Ambiguous automatic selection.
        run('clear')
        assert run('ls').stdout == ''
        directory = root / 'profile/.local/share/xrayfa'
        backups = list(directory.glob('nodes.json.backup-*'))
        assert len(backups) == 1
        assert stat.S_IMODE(backups[0].stat().st_mode) == 0o600
        assert len(json.loads(backups[0].read_text())['nodes']) == 2
        run('add', node)
        assert json.loads((directory / 'nodes.json').read_text())['nodes'][0]['id'] == 3
        run('clear', 'extra', code=2)
    print('CLI workflow passed: JSON chains, adjacent core, automatic selection, clear and IDs')


if __name__ == '__main__':
    main()
