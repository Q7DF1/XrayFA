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
        assert report["conflicts"]["status"] in {"clear", "conflict", "unknown"}
        assert isinstance(report["conflicts"]["items"], list)
        assert report["ipv6"]["policy"] == "block-non-loopback"
        assert isinstance(report["ipv6"]["globalDefaultRoute"], bool)
        ipv6_check = next(item for item in report["checks"] if item["name"] == "ipv6")
        assert ipv6_check["required"] is False
        assert ipv6_check["status"] in {"observed", "unknown"}
        check = next(item for item in report["checks"] if item["name"] == "conflicts")
        expected_status = "observed" if report["conflicts"]["status"] == "clear" else report["conflicts"]["status"]
        assert check["status"] == expected_status
        assert result.returncode in {0, 4, 5}
    print("doctor schema test passed")


if __name__ == "__main__":
    main()
