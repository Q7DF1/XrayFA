# XDRIVE storage latency probe

This standalone Go program benchmarks S3 (SigV4) and basic-auth template storage
operations without Xray, Mux, VPN routing, or WAL polling in the measurement path.
It reads an existing private **server configuration with its XDRIVE inbound first**.
For S3, it reads `service: "s3"`, `s3` endpoint/region/bucket/pathStyle and
`secrets` (access key ID, secret access key, optional session token). LIST uses
ListObjectsV2 scoped to diagnostic objects under the configured remoteFolder.
It supports the raw-byte PUT and single-step GET template used in the Koofr and pCloud tests;
OAuth, presigned multi-step operations, and template PUT body encoding are not supported.

```sh
CGO_ENABLED=0 go build -o /tmp/xdrive-latency scripts/diagnostics/xdrive-storage-latency.go
/tmp/xdrive-latency -config /private/server.json -label vps -rounds 2
```

For a standalone Android helper, build a static Linux arm64 binary, provide a
public root CA bundle, and pin a currently resolved service IPv4 address to avoid
the standalone Linux resolver's assumptions on Android:

```sh
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o /tmp/xdrive-latency-pixel scripts/diagnostics/xdrive-storage-latency.go
# After transferring the helper, a private config, and public CA bundle to the device:
/data/local/tmp/xdrive-latency-pixel -config /private/server.json -roots /private/public-roots.pem -ip SERVICE_IPV4 -label pixel-direct
```

Disable the VPN for direct-path measurements and restore it afterward. For an
unloaded baseline, pause the XDRIVE listener and run host tests sequentially.
The script itself does not change VPN, services, routing, or firewall settings.

`-ip` pins the S3 endpoint hostname (including the bucket for virtual-hosted addressing)
or the template GET hostname and preserves the operation URL's Host and TLS SNI.
The Android standalone helper expects the tested operations to use that same hostname. TLS certificate verification
remains enabled. Keep the private configuration out of reports and version control.
The program prints operation metadata and timing JSONL, never credentials or bodies.
It creates unique random test filenames and deletes them after each normal iteration.
For S3, when PUT returns `x-amz-version-id`, DELETE includes that exact version ID
to remove the uploaded test version rather than leave historical bytes and a delete marker.

Options include `-sizes 1048576` and `-rounds 1` for smaller repeat runs.
The default run tests 1 KiB, 16 KiB, 256 KiB, and 1 MiB twice, plus three cold missing
GETs and directory listings. All bulk operations share a reusable HTTP/2 connection
when the server negotiates HTTP/2.

Interpret `tcp_ms` and `tls_ms` only on new connections; zero on reused connections
means no new handshake. `first_byte_ms` and `total_ms` start at the request beginning.
`wrote_ms` is the HTTP client's write-completion callback, not a wire-level packet
capture. Download rate calculated from total time includes service waiting. Listing
bodies can be transparently decompressed and should not be compared to random binary
payloads as raw link throughput. Check HTTP status, `error`, and download `match`;
process exit status alone is not a successful-transfer assertion.
