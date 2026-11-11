# Implementation-readiness review

Status: all three requested fresh-context reviewers completed. Each confirmed
reading its persona, designated standard where supplied, target, and all
requested code/context. No failed or omitted reviewers.

## Findings and disposition

- Major R1 / K2: unchanged imported TLS values could be normalized: empty ALPN,
  an ALPN element containing a comma, blank serverName. Fixed: preserve original
  TLS fields until the corresponding form value changes. Added exact JSON
  equality and nested unknown-field regression tests.
- Major R2: nested preservation tests did not cover every promised layer or
  malformed imports. Fixed: cover root/outbound/server/user/stream/TLS/S3/
  XDRIVE unknown fields, dormant/malformed settings, invalid flow and testpre.
- Major R3 / K4: incomplete exact write allowlist. Fixed: enumerate every new
  and modified implementation/documentation path, and identify sharing tests.
- Major K1: XMUX accepted types/ranges rejected by the pinned core. Fixed:
  validate five known integer/range fields and signed int64 keepalive; retain
  unknown keys. Added invalid types, range boundaries and valid ranges tests.
- Major K3: planned XDRIVE loader fixtures were still absent. Fixed: generate
  source and runtime fixtures for all four backends; pinned loader accepted
  all eight XDRIVE fixtures and 36 other configurations.
- Nit T1: long implementation paragraph. Addressed by separating rules and
  adding a file boundary section.
- Nit T2: undefined dormant TLS/S3 terminology. Fixed: spell out settings
  present while their backend or option is inactive.
- Nit T3: awkward locale-files phrase. Fixed: name exact resource paths.

The readiness and codebase reviewers found no sharing/export bypass in the
inspected policy and Android QR/clipboard boundaries. This was code inspection;
device interaction verification is tracked separately in the consolidated
report. No claim of five-round review-loop convergence is made.

## Single readiness re-audit

Completed by a fresh codebase-details reviewer. Required reads confirmed,
including source-pinned validators. Earlier TLS, nested preservation, XMUX,
fixture and file-boundary fixes were confirmed. One Major was found: ordinary
Android copying raced dialog disposal while awaiting Room. Fixed by making
the completion callback own dismissal after the database check and clipboard
write. The real dialog/Room instrumentation regression passed. No XDRIVE
sharing/export bypass was found in the inspected paths.
