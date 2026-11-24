"""Linux native port preflight regression: python3 linux/cli/tests/test_socks_port.py."""
import ctypes
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest


class SocksPortTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix='xrayfa-port-test-')
        cls.addClassCleanup(cls.directory.cleanup)
        directory = Path(cls.directory.name)
        header = Path(__file__).resolve().parents[1] / 'src/nativeInterop/cinterop/host.h'
        source = directory / 'probe.c'
        source.write_text(f'#include "{header}"\nint probe(int port) {{ return xf_socks_port_available(port); }}\n')
        library = directory / 'probe.so'
        subprocess.run(['cc', '-shared', '-fPIC', str(source), '-o', str(library)], check=True)
        cls.library = ctypes.CDLL(str(library))
        cls.probe = staticmethod(cls.library.probe)

    def test_active_listeners_are_rejected(self):
        for address in ['127.0.0.1', '0.0.0.0']:
            for reuse in [False, True]:
                with self.subTest(address=address, reuse=reuse), socket.socket() as listener:
                    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, int(reuse))
                    listener.bind((address, 0))
                    listener.listen()
                    self.assertEqual(self.probe(listener.getsockname()[1]), 0)

    def test_immediate_restart_after_server_active_close(self):
        with socket.socket() as listener:
            listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
            listener.listen()
            with socket.create_connection(('127.0.0.1', port)) as client:
                connection, _ = listener.accept()
                connection.close()  # Server initiates close, leaving server-side TIME_WAIT.
                self.assertEqual(client.recv(1), b'')
        with socket.socket() as plain:
            with self.assertRaises(OSError):
                plain.bind(('127.0.0.1', port))
        for _ in range(5):
            self.assertEqual(self.probe(port), 1)
        with socket.socket() as restarted:
            restarted.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            restarted.bind(('127.0.0.1', port))
            restarted.listen()
            self.assertEqual(self.probe(port), 0)


if __name__ == '__main__':
    unittest.main()
