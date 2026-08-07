# XrayFA iOS App Shell (Phase E.5)

Native iOS application and Network Extension. Android lives in `:androidApp`;
shared logic is exported via `:shared` → `XrayFAShared.framework`.

## Prerequisites

- Xcode 16+
- `./scripts/build_libxray_ios.sh` (produces `AndroidLibXrayLite/LibXrayLite.xcframework`)
- `./scripts/build_hev_tun_ios.sh` (produces `tun2socks/.../HevSocks5Tunnel.xcframework`)
- Gradle iOS framework:
  ```bash
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
  # Output: shared/build/bin/iosSimulatorArm64/debugFramework/XrayFAShared.framework
  ```

## Layout (KMP convention: `androidApp/` + `iosApp/`)

```
iosApp/                     # iOS project root (this directory)
├── iosApp/                 # SwiftUI app target sources
├── PacketTunnel/           # NEPacketTunnelProvider stub
├── Config.xcconfig         # Base Xcode settings
├── iosApp.xcconfig         # App target framework search paths
├── project.yml             # xcodegen spec → iosApp.xcodeproj
└── iosApp.xcodeproj        # generated (see script below)
```

Generate Xcode project:

```bash
brew install xcodegen   # once
./scripts/generate_ios_xcodeproj.sh
```

Build (simulator, no signing):

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
```

## Current status

- **E.5a** ✅ `:shared` exports `XrayFAShared.framework`
- **E.5b** ✅ Xcode skeleton + Gradle `embedAndSignAppleFrameworkForXcode`
- **E.5c** ✅ `IosXrayBridge` cinterop + LibXrayLite link in `:core:native-bridge`
- **E.5d** ✅ App Group IPC + `IosVpnController` + PacketTunnel TUN bootstrap
- **E.5e** ✅ startLoop + HevSocks5Tunnel (hexTun path) + trial Connect in ContentView
- **E.6** ✅ Compose Multiplatform `AppShell` + `MainViewController` + iOS Koin bootstrap
