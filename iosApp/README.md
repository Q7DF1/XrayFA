# XrayFA iOS App Shell (Phase E.5)

Native iOS application and Network Extension. Android lives in `:androidApp`;
shared logic is exported via `:shared` → `XrayFAShared.framework`.

## Prerequisites

- Xcode 16+
- `./scripts/build_libxray_ios.sh` (produces `AndroidLibXrayLite/LibXrayLite.xcframework`)
- `./scripts/build_hev_tun_ios.sh` (produces `tun2socks/.../HevSocks5Tunnel.xcframework`)
- Gradle iOS framework:
  ```bash
  # Apple Silicon simulator
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
  # Intel Mac simulator
  ./gradlew :shared:linkDebugFrameworkIosX64
  # Output: shared/build/bin/<target>/debugFramework/XrayFAShared.framework
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

Build (simulator, no signing — auto-selects arm64 on Apple Silicon, x86_64 on Intel):

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
```

KMP iOS simulator targets: `iosSimulatorArm64` (Apple Silicon Mac) + `iosX64` (Intel Mac).
Both link the universal `ios-arm64_x86_64-simulator` xcframework slice.

**Compose iOS requirement**: `Info.plist` must include `CADisableMinimumFrameDurationOnPhone = true`
(Compose Multiplatform 1.7+ crashes without it). Room KSP must run for every iOS target
(`kspIosX64` when using Intel simulator).

## Current status

- **E.5a** ✅ `:shared` exports `XrayFAShared.framework`
- **E.5b** ✅ Xcode skeleton + Gradle `embedAndSignAppleFrameworkForXcode`
- **E.5c** ✅ `IosXrayBridge` cinterop + LibXrayLite link in `:core:native-bridge`
- **E.5d** ✅ App Group IPC + `IosVpnController` + PacketTunnel TUN bootstrap
- **E.5e** ✅ startLoop + HevSocks5Tunnel (hexTun path) + trial Connect in ContentView
- **E.6** ✅ Compose Multiplatform `AppShell` + `MainViewController` + iOS Koin bootstrap
- **E.6b** ✅ iOS Koin 补全 + 共享 `HomeConnectionPanel`
- **E.6c** ✅ Room/Subscription iOS Koin + ParserFactory connect
- **E.6d** ✅ 共享 Home 节点卡片 + 流量 UI（iOS 流量 stub 0）
- **E.6e** ✅ Decompose 根导航（Config / Home / Settings Tab；Home 接 SharedHomeSection）
- **E.6e-b** ✅ Intel Mac 模拟器 `iosX64` 目标
