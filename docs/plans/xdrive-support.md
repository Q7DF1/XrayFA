# VLESS XDRIVE transport support

Author: q7df1 (implementation prepared by Codex)
Date: 2026-11-11
Status: Inline VLESS integration implemented and verified. See
xdrive-review/inline_integration_report.md for focused review and evidence.

## Feature specification

XDRIVE is a VLESS transport. The user selects VLESS and Network=xdrive in the
existing node editor; storage controls expand on the same page. UUID,
Encryption, flow and TLS remain the common VLESS controls. Remove the separate
Create XDRIVE action/page and every special XDRIVE mode from global JSON import.
Global JSON import and editing remain generic, including JSON containing XDRIVE.

The pinned wrapper is AndroidLibXrayLite b5a377087e74eaa34e86718710b04c50f4b767f5,
using Xray-core 676eaf66f8f0 (26.11.9). Source inspection established:

- XDRIVE is permitted only for VLESS, requires VLESS Encryption, empty flow,
  enabled Mux, nonnegative Mux/XUDP concurrency and no UDP443 skip policy.
- REALITY is unsupported; TLS and none are available.
- Backends are google-drive, s3, template and local.
- Google Drive uses a folder ID and ordered ClientID/ClientSecret/RefreshToken.
- S3 uses endpoint, bucket, relative prefix, signing region and ordered
  AccessKeyID/SecretAccessKey/optional SessionToken. Path style defaults true.
- Template uses advanced operation JSON and ordered secrets; local uses a
  directory shared with the server and no cloud credentials.
- HTTP/3 is selected by the exact ALPN list ["h3"]. Keep core defaults for
  numerical tuning and connection reuse rather than forcing provider presets.

Cloud credentials may authorize control of the user's cloud storage.
XDRIVE clipboard export, QR sharing, JSON export and outbound preview are
prohibited. Do not invent any public or private XDRIVE share-link format.
Imported full JSON already has a nonsharing policy and retains that behavior.
Local editing controls are not an export feature.

### Architecture and persistence

NodeEditForm contains common VLESS/TLS fields plus an XDriveForm holding
backend fields and original local JSON for preservation. Only the VLESS
network options include xdrive. The helper xdriveForm merges common controls
with backend state. No cloud secrets enter encodeUrl or previewOutbound;
both explicitly reject xdrive. validateForSave checks local JSON without
opening a raw preview. Server address/port are hidden for xdrive because
storage is the transport endpoint; new source JSON uses a loopback placeholder
for the core's required VLESS server object.

Reuse local JSON persistence for app-created XDRIVE nodes until an upstream
share-link contract exists. Such records use JSON_CONFIG_PREFIX and an opaque
identifier key beginning xdrive-. This marker is an internal record identity,
not an import/export protocol or a new UI configuration type. Node.isManagedXDrive
identifies records created in the VLESS editor. Their list/home labels are
vless · xdrive, and editing opens SharedEditScreen with network=xdrive.
Global JSON imports keep their existing opaque identifiers and general raw
editor; do not auto-convert them or add a provider form to Import JSON.
Malformed managed source shapes fall back to the generic editor.

For existing records, a single UPDATE changes protocolPrefix, URL, JSON,
address, port and remark, leaving id, selection, favorite and subscription
metadata intact; countryISO is cleared when the endpoint representation changes. Saving xdrive stores source JSON with an opaque identifier;
switching back to ordinary VLESS clears persisted JSON and stores the normal
link before sharing becomes eligible again. New records use addNode.
Selected edited nodes retain the existing VPN restart behavior. Reject XDRIVE
conversion when any subscription references the node as pre/post hop or its
own subscription has a configured chain. Show an actionable remove-chain
message; never silently drop hops. The guard is enforced before persistence,
with serialized component saves and a fixed credential-safe failure code.

XDriveForm preserves root/outbound/server/user/stream/TLS/S3/advanced unknown
fields and original TLS values until corresponding controls change. Unsupported
or malformed owned subtrees do not silently normalize during parsing. Retain
invalid imported flow/Mux/testpre as validation errors. Common TLS controls
must reach generated JSON, including fingerprint, ALPN, certificate pin/name
and ECH. Existing global JSON inspection/preparation continues to preserve
outbounds while supplying the platform inlet and DNS.

### Limits and alternatives

Global JSON only was rejected because it leaves no normal VLESS configuration
UI. A separate XDRIVE wizard and global-import mode were withdrawn following
the user's clarification. A private share link was rejected due to missing
upstream standard and credential exposure. Local JSON preserves existing
core/Android/iOS start paths without adding a schema migration.

Managed XDRIVE records currently inherit local-JSON runtime limitations,
including ordinary offline delay probes, traffic stats and application proxy
chains. Display the actual feature limitation without calling XDRIVE a JSON
protocol. This change does not add OAuth login, credential provisioning,
provider quota management, or native-core upgrades. Polling/concurrency have
provider rate, battery and latency costs; leave defaults/backoff to the core.
No live account or iOS test may be claimed without corresponding evidence.

## Detailed Implementation Plan

### Exact file boundary

Create:

- domain/src/commonMain/kotlin/com/android/xrayfa/config/XDriveConfig.kt
- domain/src/commonTest/kotlin/com/android/xrayfa/config/XDriveConfigTest.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/XDriveEditorFields.kt
- androidApp/src/androidTest/java/com/android/xrayfa/XDriveSharingInteractionTest.kt

Modify:

- domain/src/commonMain/kotlin/com/android/xrayfa/model/JsonConfigNode.kt
- domain/src/commonMain/kotlin/com/android/xrayfa/repository/NodeRepository.kt
- domain/src/androidUnitTest/kotlin/com/android/xrayfa/model/CoreConfigFixturesTest.kt
- core/database/src/commonMain/kotlin/com/android/xrayfa/database/dao/NodeDao.kt
- core/data/src/commonMain/kotlin/com/android/xrayfa/repository/RoomNodeRepository.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/config/NodeEditForm.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/config/NodeFormEditor.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/config/NodeFormOptions.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/config/NodeFormValidation.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/di/SharedServicesDiModule.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/navigation/DefaultConfigComponent.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/JsonConfigEditScreen.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/SharedEditScreen.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/SharedConfigNodeRow.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/config/SharedConfigSection.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/ConfigTabScreen.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/RootContent.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/home/HomeSelectedNodeCard.kt
- shared/src/commonMain/kotlin/com/android/xrayfa/shared/ui/home/HomeLayouts.kt
- shared/src/iosMain/kotlin/com/android/xrayfa/shared/ui/platform/IosPlatformRootHooks.kt
- androidApp/src/main/java/com/android/xrayfa/ui/AndroidPlatformRootHooks.kt
- androidApp/src/main/java/com/android/xrayfa/viewmodel/XrayViewmodel.kt
- shared/src/commonMain/composeResources/values/strings.xml
- shared/src/commonMain/composeResources/values-zh-rCN/strings.xml
- shared/src/commonMain/composeResources/values-ko/strings.xml
- shared/src/commonMain/composeResources/values-ru-rRU/strings.xml
- shared/src/commonTest/kotlin/com/android/xrayfa/shared/config/NodeFormTransportTest.kt
- README.md
- docs/README_zh-CN.md
- AGENT.md

Restore the general JsonConfigEditScreen and original root routing without
XDRIVE-specific flags, navigation verbs, creation actions or auto-detection.
The obsolete xdriveContinuesNodeCreationAndReturnsToListOnBack test in
shared/src/commonTest/kotlin/com/android/xrayfa/shared/navigation/DefaultRootComponentStackTest.kt
has already been removed; its file is restored to baseline. No standalone
test file is deleted. Remove unused dedicated-creation string keys.
Keep shared editor defaults and ordinary transport behavior unchanged.

### Phases and validation

1. Domain builder: validate encryption, flow, backend credentials, uint32 tuning,
   XMUX types/ranges and TLS pins without echoing secrets. Preserve unknown
   data and original TLS values. Domain tests and source-pinned loader fixtures
   cover all four backends and full source/runtime JSON.
2. Inline form: include xdrive only in VLESS networks. Show storage controls
   below Network, reuse common VLESS/TLS controls, hide preview and irrelevant
   endpoint controls, and validate locally before saving. Atomically convert
   persistence with the single repository/DAO update described above. Tests
   cover inline save/reopen, common TLS fields, wrong protocols, prohibited
   encoder/preview, conversion identity/metadata and clearing cloud JSON. Use sentinel Google
Drive credentials and assert id, subscriptionId, favorite, selection, legacy
pre/next flags, jsonData removal and sentinel absence from ordinary URL.
Record selected-node VPN restarts and verify unselected creation does not
restart. Test both referenced-hop and own-subscription chain rejection.
3. UI/global import: managed-node edits reopen VLESS; global imports remain
   general JSON. Add-node interaction chooses Network=xdrive, fills a local
   backend, saves, reparses and converts the same record back to ordinary VLESS.
   Actual RootContent route tests must open managed records in VLESS, globally
imported XDRIVE JSON in the generic editor, and malformed managed records in
the raw fallback. Invalid inline Save retains the form/error without inserting
a record. Add a Room-backed secret-bearing conversion test with nondefault
metadata in addition to the shared fake-repository tests.
The existing ordinary-copy regression continues checking async Room reads
   and dialog disposal. Use only com.android.xrayfa.xdrivetest for device tests.
4. Verification: run domain/shared unit tests, Android compilation, the pinned
   loader script, actual local-storage VLESS Encryption/Mux traffic, and the
   isolated instrumentation tests. Rebuild APKs using the updated native AAR.
   Existing TUN recursive checkout cannot compile hev-sys.h; preserve the user's
   changes and state when cached TUN outputs are used for packaging. iOS and
   live Google Drive/S3 testing remain explicit limits on this Linux host.

The initial full EGM review and its prior interface corrections are historical
reports under xdrive-review. A focused fresh-context review is requested for
this revised inline form, persistence conversion and privacy boundary. Do not
claim that the earlier independent-page review covered this new integration.
