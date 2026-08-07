# XrayFA iOS App Shell (Phase E.5)

This directory will host the native iOS application and Network Extension.
Android remains in `:app`; shared logic is exported via `:shared` → `XrayFAShared.framework`.

## Prerequisites

- Xcode 16+
- `./scripts/build_libxray_ios.sh` (produces `AndroidLibXrayLite/LibXrayLite.xcframework`)
- Gradle iOS framework:
  ```bash
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
  # Output: shared/build/bin/iosSimulatorArm64/debugFramework/XrayFAShared.framework
  ```

## Planned layout (E.5b+)

```
app-ios/
├── iosApp/                 # SwiftUI shell + ComposeUIViewController
├── PacketTunnel/           # NEPacketTunnelProvider + tun2socks (packet mode)
└── iosApp.xcodeproj
```

## Integration checklist

1. Create Xcode project with App + Network Extension targets
2. Link `XrayFAShared.framework` (KMP) and `LibXrayLite.xcframework` (gomobile)
3. Configure App Group + VPN entitlements (Apple approval required)
4. Replace `IosVpnController` / `IosXrayBridge` stubs with Extension IPC
5. Wire `SettingsDataStoreContext` to App Group path (see `:core:datastore` iosMain)

## Current status (E.5a)

- `:shared` KMP module aggregates domain + core + platform:vpn for iOS export
- No Xcode project yet — Android runtime unchanged
