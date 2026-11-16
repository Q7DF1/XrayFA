# XDRIVE storage consistency patch

Target: Xray-core `676eaf66f8f0` (`v1.260327.1-0.20261109052709-676eaf66f8f0`).

Apply `xdrive-storage-consistency.patch` with `patch -p1` inside a writable copy
of that exact core source. Do not modify the shared Go module cache.

Changes:

- Retry empty `.seg` reads without advancing or deleting the segment. Koofr can
  expose an empty object while PUT is still in progress; accepting it loses
  encrypted records and causes the next record's authentication to fail.
- Read known segment keys directly for flattened templates, rather than downloading
  the entire directory for every reader poll. Probe end/error markers at a lower
  frequency so idle metadata requests do not block the next data read. End-of-stream
  notification can be delayed by the five-second control probe interval.
- Delete flattened session descendants with a separator boundary. Deleting the
  synthetic directory alone leaves all objects behind and inflates every poll.

Validation:

```sh
go test ./transport/internet/xdrive -count=1
```

Regression tests cover transient empty reads, preserving byte order and avoiding
premature deletion, direct reads without directory enumeration, delayed end markers, recursive cleanup, neighboring sessions, and idempotence.

For Android, use a temporary AndroidLibXrayLite checkout with a local `replace`
for `github.com/xtls/xray-core` pointing to the patched core, then run the usual
`gomobile bind`. Keep the dependency version pinned. The patch is retained here
because Xray-core source is an external Go dependency, not part of this repository.

This handles the confirmed empty-object race. It does not add checksums to the
wire format or establish that a provider never exposes non-empty partial data.
