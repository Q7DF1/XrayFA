# iOS Status: Stubs and Platform Gaps

Document intentional stubs and unresolved platform gaps as required by `AGENT.md` (R-3). Move resolved entries to the implemented section or remove them. This English translation preserves the status recorded on 2026-09-04; it does not represent a new implementation audit.

## Intentional Stubs

These behaviors are deliberate. Do not move tunnel or core execution into the host app.

| Location | Behavior | User-visible? | Reason |
|----------|----------|---------------|--------|
| `core/native-bridge/.../NativeBridgeFactory.ios.kt`: `IosStubTunBridge` | `startTun2Socks` / `stop` / `isRunning` return false | No | TUN runs through HevSocks5Tunnel in PacketTunnel, not the KMP `TunBridge` |
| `shared/.../IosXrayCore.kt`: `startXrayCore` | Returns `false` | No | Xray runs only in the Network Extension process |
| `IosXrayCore.measureDelaySync` | Returns `-1L` | Partly | The host has no live core; Home falls back to `measureOutboundDelay`, which requires an active VPN |
| `common/.../GeoLiteDownloadSupport.ios.kt`: `geoLiteDownloadSupported` | Returns `false` | Yes: download button disabled | The host cannot reach the Network Extension's local SOCKS endpoint |

## Unresolved User-Visible Features

| Hook / Feature | Current UI or Behavior | Impact / Constraint |
|----------------|------------------------|---------------------|
| `AppsScreen`: per-app proxy | `SharedInDevelopmentScreen` | Unavailable; system API limitations require a product decision |
| Settings: geoip.dat / geosite.dat import | Shows an in-development notice | Unavailable for users who need routing data import |
| Settings: HexTun | Shows an in-development notice | Network Extension uses hev-socks5; explain this limitation in the UI |
| QR scanner: photo library / flashlight | AVFoundation live camera only | Photo import and flashlight controls unavailable |
| `SystemBackHandler` | Empty implementation | Relies on system gestures |
| Logs | `SharedInProcessAppLogScreen` backed by `AppLogStore` | Host-process logs only; Network Extension log bridge remains unresolved |

## Implemented

| Feature | Implementation |
|---------|----------------|
| `IosXrayConfigEncoder` | Delegates to `KotlinxXrayConfigEncoder` |
| `IosGeoIpProvider` | Reads GeoLite2 MMDB from the App Group and provides country flag emoji |
| Digest / `calculateBytesHash` | CommonCrypto SHA-256 / MD5 |
| `XrayBridge.measureOutboundDelay` | Objective-C shim `XrayFAMeasureOutboundDelay` |
| Theme: `darkMode` | `AppShell` and `XrayTheme` read DataStore |
| `ShareNode` | QR code dialog and clipboard export |
| `BugReport` | Shared form and GitHub issue URL through `SharedBugReport` and `BugReportIssueComposer` |

## Update Rule

Any new iOS `actual` that returns a placeholder constant, throws `UnsupportedOperationException`, or displays an in-development screen must be recorded here in the same PR. Do not mark the corresponding gap as resolved while the placeholder remains.
