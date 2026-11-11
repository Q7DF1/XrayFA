# Design, structure and style review

Status: all three fresh-context reviewers completed and confirmed required
persona, target and supplied standard/context reads.

- Major D1 (design): silent normalization could drop dormant TLS/S3 fields or
  malformed owned types on structured save. Fix applied: classify these shapes
  for raw editing; regression tests cover this classification.
- Major S1 (structure): cloud rate limits, polling/concurrency resource use
  and their mitigations were missing from the risk section. Fix applied:
  retain core defaults/backoff, allow tuning, state quota/latency limits.
- Nit T1 (style): make the HTTP/3 list condition explicit. Fix applied:
  exactly ["h3"], rather than an ambiguous contains condition.
- Nit T2 (style): remove duplicate preview/export sentence in risks. Fix applied.

The design critic otherwise found motivation, scope, alternatives, privacy
requirements and JSON limitations covered. The structural reviewer found
heading levels and required feature-spec sections consistent.
