# Linux CLI

A native Linux x86-64 SOCKS5/TUN client. No JVM is needed at runtime.
The listener is **127.0.0.1:10808**. The default SOCKS5 mode runs without root
and leaves system routes, DNS and firewall rules untouched.

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
Ctrl+C stops the foreground session. Programs can use SOCKS5 at `127.0.0.1:10808`:

```sh
curl --proxy socks5h://127.0.0.1:10808 --noproxy '' --max-time 30 https://www.google.com/
```

## Commands

| Command | Purpose |
| --- | --- |
| `add FILE` | Import a link or JSON file |
| `ls` | List saved nodes |
| `run [ID] [--tun]` | Start SOCKS5; optionally enable system TUN routing and DNS |
| `stop` / `recover` | Stop a TUN session / restore retained TUN resources (root) |
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
SOCKS5 JSON composition preserves outbound chains, DNS, routing and policy while replacing
listeners and logs. Inbound-specific routing, external file references, and
unrelated top-level services are rejected. Configuration validation cannot prove
remote authentication or connectivity: test real traffic separately.

The supervisor rejects duplicate sessions and occupied ports, drains bounded core
output, redacts storage credentials, and terminates its child on shutdown or parent
death. SIGKILL may leave a private temporary config. Logs rotate at 5 MiB with
three histories.

## TUN mode

Import the node as your ordinary user, then explicitly start with root privileges:

```sh
./xrayfa add node.txt
sudo ./xrayfa run --tun
# Or select a saved node:
sudo ./xrayfa run 1 --tun
```

TUN mode also listens on `127.0.0.1:10808`. It requires a compatible core with
the `tun` inbound, `/dev/net/tun`, CAP_NET_ADMIN, iproute2, nftables, and a supported
systemd-resolved or static resolv.conf setup. It takes over IPv4 routing and DNS
and blocks external IPv6 during the session. Other VPN interfaces and nonstandard
policy routing are rejected before network changes. `doctor` and `check` currently
describe/validate SOCKS5; TUN performs its own preflight and final configuration
validation at startup.
TUN JSON input must contain one outbound and no custom `routing`, `dns` or
`policy` section. Unsupported imported policies/chains are rejected before
network changes, rather than silently discarded. Outbound transport settings,
including an explicitly configured Mux, are retained.

Route comparisons ignore dump enumeration order and expiration countdowns.
While TUN is active, the sole IPv4 DHCP underlay default may toggle the standard
NetworkManager connectivity metric penalty of 20000; its gateway, interface and
every other route attribute must stay unchanged. Shutdown waits up to 2.5 seconds
for that metric to return to the startup baseline, without rewriting the system
route. Other route changes still stop the session, log added/removed entries and
retain an observed route snapshot in the recovery archive or journal.
The delete/add gap of that same DHCP-default replacement is rechecked for at most
250 ms while TUN and all other routes remain unchanged. An unresolved gap or a
different route change still stops the session.

Ctrl+C, SIGTERM and SIGHUP restore owned network resources. From another terminal,
use `sudo ./xrayfa stop`. After a forced controller termination, use
`sudo ./xrayfa recover`. Recovery validates resource ownership and preserves
`/run/xrayfa/session.json` with exit 9 if restoration is incomplete. These commands
also support retained journals from older TUN versions; they do not stop SOCKS5
sessions. With an isolated profile, preserve it explicitly:
`sudo env XRAYFA_HOME=/absolute/profile ./xrayfa run --tun`.

Stopping restores ordinary system networking. This is not a kill switch:
applications may connect directly after TUN stops. An unexpected failure reports
its cause and a final restoration status. Incomplete restoration requires
`recover`; the supervisor does not automatically restart and take over networking
again. A successful rollback does not certify leak prevention or uninterrupted
application connections.

The new listener port is 10808; building a separate executable leaves an older
listener on 10999 running. Enabling system TUN can still affect its network path.

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
python3 linux/cli/tests/run_socks_smoke.py --cli /path/xrayfa --core /path/xray --local-only
python3 linux/cli/tests/run_web_acceptance.py --proxy socks5h://127.0.0.1:10808 --out /tmp/new-web-acceptance
```

The matrix uses synthetic credentials and independently checks 1402 protocol
cases. Live smoke tests use a temporary local VLESS server, check HTTPS status
codes, duplicate-session rejection and cleanup. `--local-only` checks an exact
HTTP response through the real VLESS and SOCKS5 data path without Internet access.
An optional
`--upstream-socks-port PORT` uses an existing loopback Internet exit without
reading its configuration. Historical TUN suites under `tests/runtime` and
`tests/vm` require disposable VMs. Never commit operator inputs or raw reports.

Inside the verified dedicated VM, `tests/runtime/fixtures/tun_smoke.py` accepts
`--instance UUID --cli PATH --core PATH --link-file PRIVATE_FILE --out NEW_DIRECTORY`.
It checks TUN and SOCKS5 HTTPS traffic, system DNS, occupied-port and active-session
refusals, signal shutdown, `stop`, core crash, controller SIGKILL/recovery, and
preservation of externally added routes. Each lifecycle case compares network
state with its baseline. `--skip-traffic` runs only lifecycle checks and makes
no remote connectivity claim. Keep the link file readable by the VM's `xfa` user
and outside source control.

Add `--soak-seconds 600 --soak-interval 30 --request-timeout 45` for a monitored
ten-minute session before the lifecycle cases. `--web-runner PATH` selects
`run_web_acceptance.py` when the scripts are copied into a guest. The fixture
checks runtime logs four times per second, runs repeated direct HTTPS rounds,
blocks both SOCKS ports and physical-interface direct HTTPS, captures the TUN and
upstream paths, and exercises DHCP connectivity metric replacements. Unexpected
log warnings/errors, failed traffic, premature exit, or incomplete restoration
fail the run. It requires a compatible private node input, and evidence must stay
outside source control. Configuration/lifecycle checks alone are not a soak pass.

Actual website acceptance is separate from configuration and lifecycle checks.
`run_web_acceptance.py` makes three rounds of HTTPS requests to Google,
Cloudflare, GitHub and Wikipedia, checking HTTP 200, certificate verification and
response content. Every sample must succeed; failed attempts remain failed.
Use `--direct` instead of `--proxy` to test an already running TUN session's
system route, inside a disposable VM. Reports store status, size and body hashes
without storing response bodies or node credentials.

For a VLESS node whose repeated connection handshakes are slow, an imported JSON
outbound can explicitly enable `"mux":{"enabled":true,"concurrency":8,
"xudpProxyUDP443":"skip"}` to reuse TCP proxy connections. This is a per-node
setting; share links keep their existing defaults. Compare cold startup as well
as subsequent requests before declaring a particular node accepted.
