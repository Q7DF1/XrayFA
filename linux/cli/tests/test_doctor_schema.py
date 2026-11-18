import json
import os
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / "build" / "bin" / "linuxX64" / "releaseExecutable" / "xrayfa.kexe"


def main() -> None:
    if not CLI.is_file():
        raise SystemExit(f"build the CLI first: {CLI}")
    with tempfile.TemporaryDirectory(prefix="xrayfa-doctor-") as raw:
        root = Path(raw)
        core = root / "core"
        core.write_text("#!/bin/sh\n[ \"$1\" = version ] || exit 2\necho 'Xray fixture doctor'\n")
        core.chmod(0o700)
        env = dict(os.environ, XRAYFA_HOME=str(root / "profile"))
        result = subprocess.run(
            [str(CLI), "doctor", "--json", "--core", str(core)],
            env=env, text=True, capture_output=True, check=False,
        )
        report = json.loads(result.stdout)
        assert report["operation"] == "doctor"
        assert report["readOnly"] is True
        assert report["mode"] == "socks5"
        assert report["listen"] == "127.0.0.1"
        assert report["port"] == 10808
        assert report["readyForRun"] is True
        assert report["runtimeCertified"] is False
        assert len(report["coreSha256"]) == 64
        assert report["corePath"] == str(core)
        assert result.returncode == 0
    print("doctor schema test passed")


if __name__ == "__main__":
    main()
