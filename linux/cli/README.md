# Linux CLI

A native Linux x86-64 SOCKS5 client. No JVM or root is needed at runtime.
The listener is **127.0.0.1:10999**. System routes, DNS and firewall rules are untouched.

## Quick start

Place `xrayfa` and a compatible native `xray` core together, then:

```sh
./xrayfa add node.txt
./xrayfa run
```

`node.txt` contains one share link or a complete JSON outbound configuration.
XDRIVE requires JSON with complete storage settings; basic XDRIVE share links
omit those settings. Keep input files private (`chmod 600 node.txt`).
When several nodes are saved, use `./xrayfa ls`, then `./xrayfa run ID`.
Ctrl+C stops the foreground session. Programs can use SOCKS5 at `127.0.0.1:10999`:

```sh
curl --proxy socks5h://127.0.0.1:10999 --noproxy '' --max-time 30 https://www.google.com/
```

## Commands

| Command | Purpose |
| --- | --- |
| `add FILE` | Import a link or JSON file |
| `ls` | List saved nodes |
| `run [ID]` | Start; automatically selects a sole saved node |
| `clear` | Back up and empty both current and legacy stores |
| `doctor` | Probe the core dependency |
| `check ID --output FILE` | Save and validate the exact SOCKS5 configuration |
| `logs [--tail N] [--follow]` | Read redacted logs |

`--core PATH` overrides core discovery: adjacent `xray`, `~/app/Xray/xray`, then
`~/Xray/xray`. `--log-level debug|info|warning|error` applies to run/check.
`add --link URI` and `add --link-file FILE` remain supported; prefer files to keep
credentials out of shell history and process arguments. `--help` lists options.

Private data lives in `~/.local/share/xrayfa`, logs in `~/.local/state/xrayfa/logs`.
`XRAYFA_HOME` chooses an isolation root. Directories are 0700, files 0600.
`clear` preserves the next ID and makes private backups containing credentials;
it does not stop an active session. Unsafe or corrupt stores are rejected.
JSON import preserves outbound chains, DNS, routing and policy while replacing
listeners and logs. Inbound-specific routing, external file references, and
unrelated top-level services are rejected. Configuration validation cannot prove
remote authentication or connectivity: test real traffic separately.

The supervisor rejects duplicate sessions and occupied ports, drains bounded core
output, redacts storage credentials, and terminates its child on shutdown or parent
death. SIGKILL may leave a private temporary config. Logs rotate at 5 MiB with
three histories. Legacy `stop`/`recover` are retained solely for previous TUN sessions.

Exit codes: 0 success; 2 usage; 3 missing node; 4 permissions; 5 core dependency;
6 invalid configuration; 7 execution; 8 conflict; 9 legacy recovery; 10 I/O; 11 logging.

## Build and test

Use the repository JDK/Gradle toolchain and system OpenSSL libcrypto (1.1+).

```sh
./gradlew -PenableLinuxCli=true :linux:cli:linkReleaseExecutableLinuxX64
./gradlew -PenableLinuxCli=true :linux:cli:jvmTest :linux:cli:linuxX64Test
```

Executable: `linux/cli/build/bin/linuxX64/releaseExecutable/xrayfa.kexe`.
Linux targets are opt-in and do not change the mobile target graph.
Use an immutable executable and new output directories for integration tests:

```sh
python3 linux/cli/tests/run_matrix.py --cli /path/xrayfa --core /path/xray --out /tmp/new-matrix
python3 linux/cli/tests/run_acceptance.py --cli /path/xrayfa --out /tmp/new-acceptance
python3 linux/cli/tests/test_cli_workflow.py --cli /path/xrayfa --core /path/xray
python3 linux/cli/tests/run_socks_smoke.py --cli /path/xrayfa --core /path/xray
```

The matrix uses synthetic credentials and independently checks 1402 protocol
cases. Live smoke tests use a temporary local VLESS server, check HTTPS status
codes, duplicate-session rejection and cleanup. An optional
`--upstream-socks-port PORT` uses an existing loopback Internet exit without
reading its configuration. Historical TUN suites under `tests/runtime` and
`tests/vm` require disposable VMs. Never commit operator inputs or raw reports.
