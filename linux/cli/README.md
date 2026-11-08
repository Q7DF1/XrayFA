# XrayFA Linux CLI

A Kotlin/Native executable for Ubuntu 18.04+ x86-64. Ubuntu 18.04 is the
compatibility target for the runtime path. The CLI parses share links, saves
nodes, validates the exact final configuration with an external Xray
**`run -test`**, and can run a supervised TUN session with DNS, routes and
IPv6 protection.

## Build and use

Use the repository's existing JDK/Gradle/Kotlin versions and an installed Ubuntu
18.04-compatible crypto libraries (`libcrypto.so.1.1` or newer). No JVM is
required to run the resulting CLI.
The first build downloads the Kotlin/Native toolchain. Linux is disabled by
default in the mobile build graph.

```sh
./gradlew -PenableLinuxCli=true :linux:cli:linkReleaseExecutableLinuxX64
CLI="$PWD/linux/cli/build/bin/linuxX64/releaseExecutable/xrayfa.kexe"
"$CLI" --help
"$CLI" add --link-file ./one-share-link.txt
"$CLI" ls
"$CLI" check 1 --output "$PWD/node-1.runtime.json" --core /usr/local/bin/xray
"$CLI" logs --tail 100
"$CLI" logs --follow
sudo "$CLI" doctor --json
sudo "$CLI" run 1 --core /usr/local/bin/xray --log-level info
sudo "$CLI" stop
sudo "$CLI" recover
```

`--link URI` is also supported; it exposes the link in shell history and process
arguments. A link file must contain exactly one link. `add` does not prove that a
remote node works. ID allocation is persistent and serialized across processes.

The CLI uses the invoking user's passwd home directory. `XRAYFA_HOME` explicitly
overrides the profile directory for isolated tests. Nodes live in
`~/.local/share/xrayfa/nodes.json`; logs live in
`~/.local/state/xrayfa/logs/xrayfa.log`. Application directories are private
(0700); node, config, report and log files are 0600. Existing unsafe permissions,
symlinks and corrupt stores are rejected. Original share links remain in the
private store; Linux compatibility conversions never overwrite them.

`check` requires a new `--output` path. It also reserves a sibling
`*.validation.json` report. The report records the node/session, UTC time, exact
config and core paths/hashes, core version/exit status, timeout and log location.
Changing the config invalidates the report. Config files contain credentials;
reports and terminal/file logs are redacted. The default core is the invoking
user's `~/app/Xray/xray`; no core is bundled or downloaded at runtime.

## Install, upgrade and rollback

`tools/install.sh` installs a tested CLI and optional core as a staged pair.
Process-visible publish failures restore the previous CLI/core/manifest pair.
It refuses to replace files while `/run/xrayfa/session.json` exists, preserves
timestamped `.previous.*` artifacts, publishes a hash manifest, and never
changes routes, DNS, TUN devices or firewall state.

```sh
sudo linux/cli/tools/install.sh \
  --cli linux/cli/build/bin/linuxX64/releaseExecutable/xrayfa.kexe \
  --core /usr/local/bin/xray
sudo /usr/local/bin/xrayfa doctor --json
sudo linux/cli/tools/install.sh --rollback
```

Use `--dry-run` to validate inputs, hashes, existing artifact types, and the
managed `bin/xrayfa` link without creating or replacing files. It rejects an
external or unsafe symlink exactly as a real install would. A rollback is also
refused while a runtime journal is present; stop the session or complete
recovery first.

The fixed argv is `CORE run -test -c ABSOLUTE_FILE`. The child gets an explicit
clean environment, including an asset directory beside the selected executable.
Both output pipes are drained, with a 30 second timeout, process group termination
and child reaping. CLI and core diagnostics appear on stderr. Command results
and `logs` output appear on stdout. Successful validation means configuration
acceptance by that particular core, not working traffic, routing or authentication.

## Logging and errors

Use `--log-level debug|info|warning|error` with `check` (default `info`). This also
sets the generated core log level; access logging is disabled. Every check has
START/END records. A background file writer uses bounded queues with reserved
capacity for errors and session boundaries; overflow counts are visible. Lines
are capped, control characters are removed and known credentials/authorization
values are redacted before enqueueing. Output continues on the terminal if the
file writer fails.

Each file is limited to 5 MiB, retaining the current file plus three histories.
`logs --tail N` accepts 0..10000 lines and reads backwards across histories.
`--follow` drains the old file before opening its replacement after rotation.
Ctrl+C exits the viewer. A missing/unreadable log is an error.

| Exit | Meaning in this version |
|---|---|
| 0 | Command succeeded; for check, exact final config accepted by core |
| 2 | Usage or link-source count error |
| 3 | Node ID missing |
| 4 | Output/core permission failure |
| 5 | Missing/nonworking core dependency |
| 6 | Unsupported/invalid link, config or nonzero core validation |
| 7 | Core execution timeout or interruption |
| 8 | Output/report conflict |
| 9 | Runtime recovery is incomplete; journal and evidence are retained |
| 10 | Store or file I/O/schema/ownership error |
| 11 | Log initialization, reading or persistence failure |

Shared App parsers, defaults, native libraries and data remain unchanged. Linux
composes its own TUN/DNS/routing JSON around the shared outbound. Runtime
sessions are journaled and supervised; `stop` and `recover` verify ownership
before removing resources. IPv6 output is protected by a session-owned nftables
policy that blocks non-loopback IPv6 unless a future IPv6-capable runtime plan
explicitly changes that policy.

`doctor --json` includes a non-blocking `ipv6` capability object with the current
policy, whether a global IPv6 default route was observed, and a status such as
`global-route-observed` or `link-local-only-or-no-default`. This reports
capability only; it does not claim an IPv6 proxy data-path test passed.

## Reproduce validation

The frozen manifests and scripts are under `tests/`. All credentials are local
synthetic values. `manifest-full.json` includes the base 54-cell protocol matrix,
all applicable flow/VMess cipher/transport mode combinations, advanced parameter
and endpoint boundaries, aliases, negative cases and explicit N/A entries.
Independent Python assertions verify final fields and credentials; accepted cases
must also pass the designated core. Add-stage and core-stage rejections are
reported separately per case. Do not edit expectations to conceal failures.

```sh
./gradlew -PenableLinuxCli=true :common:linuxX64Test :domain:linuxX64Test
./gradlew :common:testDebugUnitTest :domain:testDebugUnitTest :androidApp:compileDebugKotlin
python3 linux/cli/tests/run_matrix.py --cli "$CLI" --out /tmp/xrayfa-matrix-new
python3 linux/cli/tests/run_acceptance.py --cli "$CLI" --out /tmp/xrayfa-acceptance-new
```

Output directories must be new. Matrix output includes `coverage.md`,
`coverage.json`, the frozen manifest and per-case final JSON, hashes, assertions,
validation reports and redacted logs. Acceptance covers concurrent IDs, corrupt
stores, permissions, output conflicts, real process failures, bounded pipes,
queue overflow/error priority, redaction, levels, rotation/tail/follow, log
failures, simultaneous failure ordering, timeout, signals and environment/argv isolation. Test core fixtures are
used only for deterministic failure/log injection; protocol acceptance uses
`/usr/local/bin/xray`.

Do not rebuild/replace the executable during validation. Retain its SHA-256 with
reports. The local delivery directory is `build/delivery/`; generated artifacts
and historical investigation logs are not source files. Keep machine-specific
acceptance reports and operator inputs outside version control. External IPv6
validation requires a test target with global IPv6 connectivity.

To package a verified immutable copy, place the tested binary at
`linux/cli/build/delivery/xrayfa` and run:

```sh
python3 linux/cli/tests/package_delivery.py --matrix /tmp/xrayfa-matrix-new --acceptance /tmp/xrayfa-acceptance-new
```

The packager requires matching executable hashes in both reports, preserves
historical failures separately, records dependency/ELF/test evidence and produces
`build/xrayfa-linux-x64-config.tar.gz` with checksums. Failure-injection symlinks
are recorded as text markers; devices and active symlinks are never archived.

## Synthetic node fixtures

The committed test manifests contain synthetic share links, not deployed nodes.
Configuration fixtures use reserved example domains, documentation IP ranges,
and the explicit test UUID `00000000-0000-4000-8000-000000000001`.
Static cryptographic test inputs are generated from deterministic byte sequences.
Runtime fixtures create their own endpoints inside the disposable VM network
(`10.204.0.0/24`); runtime private keys are generated inside the guest.
Do not replace these fixtures with subscription data or production credentials.
