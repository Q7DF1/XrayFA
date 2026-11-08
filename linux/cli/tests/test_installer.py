import hashlib
import os
import shutil
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "tools" / "install.sh"


def write_executable(path: Path, version: str, command: str) -> None:
    path.write_text(f"#!/bin/sh\nif [ \"$1\" = \"{command}\" ]; then echo {version}; exit 0; fi\nexit 2\n")
    path.chmod(0o700)


def run(*args: str, extra_env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    command = [str(INSTALLER), *args]
    env = os.environ.copy()
    if extra_env:
        env.update(extra_env)
    if shutil.which("unshare"):
        sandbox = subprocess.run(
            ["unshare", "-Urmpf", "sh", "-c", "mount -t tmpfs tmpfs /run && exec \"$@\"", "x", *command],
            env=env, text=True, capture_output=True, check=False,
        )
        if sandbox.returncode != 1 or "Operation not permitted" not in sandbox.stderr:
            return sandbox
    return subprocess.run(command, env=env, text=True, capture_output=True, check=False)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="xrayfa-installer-") as raw:
        root = Path(raw)
        prefix = root / "lib"
        bindir = root / "bin"
        cli = root / "cli"
        core = root / "core"
        write_executable(cli, "cli-v1", "--version")
        write_executable(core, "core-v1", "version")

        result = run("--dry-run", "--cli", str(cli), "--core", str(core), "--prefix", str(prefix), "--bin-dir", str(bindir))
        assert result.returncode == 0, result.stderr
        assert not prefix.exists()

        result = run("--cli", str(cli), "--core", str(core), "--prefix", str(prefix), "--bin-dir", str(bindir))
        assert result.returncode == 0, result.stderr
        assert (prefix / "xrayfa").read_bytes() == cli.read_bytes()
        assert (prefix / "xray").read_bytes() == core.read_bytes()
        assert (bindir / "xrayfa").is_symlink()
        assert (bindir / "xrayfa").resolve() == prefix / "xrayfa"
        manifest = (prefix / "manifest.txt").read_text()
        assert f"cliSha256={digest(cli)}" in manifest
        assert f"coreSha256={digest(core)}" in manifest

        write_executable(cli, "cli-v2", "--version")
        write_executable(core, "core-v2", "version")
        fakebin = root / "fakebin"
        fakebin.mkdir()
        fake_mv = fakebin / "mv"
        fake_mv.write_text(
            "#!/bin/sh\n"
            "target=\"\"\n"
            "for arg in \"$@\"; do target=\"$arg\"; done\n"
            f"[ \"$target\" = \"{prefix / 'xray'}\" ] && exit 73\n"
            "exec /bin/mv \"$@\"\n"
        )
        fake_mv.chmod(0o700)
        result = run(
            "--cli", cli, "--core", core, "--prefix", prefix, "--bin-dir", bindir,
            extra_env={"PATH": f"{fakebin}:{os.environ['PATH']}"},
        )
        assert result.returncode == 10
        assert (prefix / "xrayfa").read_bytes() != cli.read_bytes()
        assert (prefix / "xray").read_bytes() != core.read_bytes()
        assert f"cliSha256={digest(prefix / 'xrayfa')}" in (prefix / "manifest.txt").read_text()

        result = run("--cli", str(cli), "--core", str(core), "--prefix", str(prefix), "--bin-dir", str(bindir))
        assert result.returncode == 0, result.stderr
        assert (prefix / "xrayfa").read_bytes() == cli.read_bytes()
        assert (prefix / "xray").read_bytes() == core.read_bytes()
        backups = sorted(prefix.glob("xrayfa.previous.*"))
        assert len(backups) == 1
        assert len(list(prefix.glob("xray.previous.*"))) == 1

        result = run("--rollback", "--prefix", str(prefix), "--bin-dir", str(bindir))
        assert result.returncode == 0, result.stderr
        assert (prefix / "xrayfa").read_bytes() != cli.read_bytes()
        assert (prefix / "xray").read_bytes() != core.read_bytes()
        assert (bindir / "xrayfa").resolve() == prefix / "xrayfa"

        rollback_external = root / "rollback-external"
        write_executable(rollback_external, "rollback-external", "--version")
        (prefix / "xrayfa").unlink()
        (prefix / "xrayfa").symlink_to(rollback_external)
        result = run("--rollback", "--prefix", prefix, "--bin-dir", bindir)
        assert result.returncode == 8
        assert (prefix / "xrayfa").resolve() == rollback_external
        assert rollback_external.read_bytes() != cli.read_bytes()
        (prefix / "xrayfa").unlink()
        (prefix / "xrayfa").write_bytes(rollback_external.read_bytes())
        (prefix / "xrayfa").chmod(0o700)

        # Dry-run must reject the same external symlink that a real install
        # would reject, without replacing the link or creating a prefix.
        dry_unsafe = root / "dry-unsafe"
        dry_unsafe.mkdir()
        dry_external = root / "dry-external"
        write_executable(dry_external, "dry-external", "--version")
        (dry_unsafe / "xrayfa").symlink_to(dry_external)
        result = run(
            "--dry-run", "--cli", cli, "--prefix", dry_unsafe,
            "--bin-dir", root / "dry-unsafe-bin",
        )
        assert result.returncode == 8
        assert (dry_unsafe / "xrayfa").resolve() == dry_external
        assert not (root / "dry-unsafe-bin").exists()

        linked_prefix = root / "linked-prefix"
        linked_prefix_target = root / "linked-prefix-target"
        linked_prefix_target.mkdir()
        linked_prefix.symlink_to(linked_prefix_target, target_is_directory=True)
        result = run("--dry-run", "--cli", cli, "--prefix", linked_prefix, "--bin-dir", root / "linked-bin")
        assert result.returncode == 8
        assert not (linked_prefix_target / "xrayfa").exists()

        unsafe = root / "unsafe"
        unsafe.mkdir()
        external = root / "external"
        write_executable(external, "external", "--version")
        (unsafe / "xrayfa").symlink_to(external)
        result = run("--cli", str(cli), "--prefix", str(unsafe), "--bin-dir", str(root / "unsafe-bin"))
        assert result.returncode == 8
        assert external.read_bytes() != cli.read_bytes()

    print("installer tests passed")


if __name__ == "__main__":
    main()
