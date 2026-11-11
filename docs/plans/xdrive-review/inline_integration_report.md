# Inline VLESS integration review

The user clarified that XDRIVE belongs in VLESS Network settings, without a
separate page/action or special global JSON importer. The implementation now
expands backend controls beneath Network=xdrive in SharedEditScreen and reuses
the existing VLESS/TLS fields. Generic JSON importing remains generic.

## Fresh-context reviews

Three native Goldfish reviewers completed and confirmed all required reads:
codebase_details with implementation-readiness standard; design_critic with
feature-spec standard; implementation_readiness with its standard. Each read
the current design and designated implementation/test context. No failed
reviewer is counted as clean, and no previous independent-page audit is
represented as covering this new integration.

## Findings and resolutions

- Design Major D1: in-place conversion could remove referenced or inherited
  proxy-chain hops. Fixed: before persistence, check all subscription pre/post
  references and the record's own subscription chain; reject conversion with
  the fixed XDRIVE_CHAIN error and localized remove-chain instructions. Saves
  are serialized by the config component. Tests cover pre-hop, post-hop and
  selected own-subscription cases without mutation or VPN restart.
- Design Nit D2: inline limitations were omitted. Fixed: localized text explains
  offline delay and application-chain limits in the inline controls. Home
  statistics messaging names XDRIVE rather than its internal JSON storage.
- Readiness R1 / Codebase C1: exact file boundary omitted generic editor restore
  and obsolete test disposition. Fixed: enumerate the file and specify that
  the obsolete method in DefaultRootComponentStackTest was already removed;
  no standalone test file was deleted.
- Readiness R2 / Codebase C2: fake tests did not prove metadata, cloud-secret
  removal or selected restart behavior. Fixed: use sentinel Google Drive
  credentials, nondefault identity/subscription/selection/favorite/pre/post
  metadata, restart counters, JSON removal and sentinel absence assertions.
  Add a real Room conversion round-trip through the actual DAO UPDATE.
- Readiness R3: direct helper parsing did not prove actual route behavior or
  invalid-save behavior. Fixed: render RootContent, open managed records via
  node editing, open global imported XDRIVE JSON through the generic route,
  exercise malformed-managed fallback, and assert invalid inline Save retains
  the error/form without adding a record.

The codebase reviewer found no concrete privacy escape or conversion data-loss
defect in the inspected implementation. Both encodeUrl and previewOutbound
reject XDRIVE; normal conversion removes persisted cloud JSON in the same
UPDATE before the record becomes shareable. Country summary is intentionally
cleared when its endpoint representation changes.

## Verification

- Domain: 78 tests passed.
- Shared: 74 tests passed, including inline save/reopen, common TLS fields,
  credential-safe conversion, restart and proxy-chain safeguards.
- Android compilation and isolated test-APK assembly passed.
- Emulator AndroidJUnitRunner: OK (5 tests), 26.087 seconds. Tests cover actual
  managed/global/malformed edit routing; generic global JSON input; inline
  VLESS creation and reparse; real Room metadata/secret conversion; and the
  ordinary clipboard/disposal regression. Test records are cleaned up.
- Source-pinned loader accepted 44 generated configurations, including eight
  XDRIVE source/runtime fixtures for all four storage backends.
- Fresh local-storage traffic verification passed: 1,102,000 bytes through
  generated runtime JSON, VLESS Encryption and Mux, payload SHA-256
  8d0ef6d104b3f118d7f79063467d12c666a648d9ee511c0a1aedd2a58b4c9d70.

Release packaging retains the previously documented cached-TUN limitation:
the existing recursive TUN checkout cannot build hev-sys.h. Its source and
recursive revisions were not modified. Real Google Drive/S3 connectivity and
iOS runtime/compilation were not tested on this Linux host.
