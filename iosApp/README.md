# XrayFA iOS App Shell (Phase E.5)

Native iOS application and Network Extension. Android lives in `:androidApp`;
shared logic is exported via `:shared` → `XrayFAShared.framework`.

## Prerequisites

- Xcode 26+ with the iOS 26 SDK for the native Liquid Glass UI. Running an iOS 26
  simulator alone is insufficient: apps linked against an older SDK use the legacy design.
- Apple Silicon Mac for the current Compose Multiplatform 1.12 UI simulator target.
  Compose 1.11+ no longer supplies the Intel (`iosX64`) UI artifacts.
- `./scripts/build_libxray_ios.sh` (produces `AndroidLibXrayLite/LibXrayLite.xcframework`)
- `./scripts/build_hev_tun_ios.sh` (produces `tun2socks/.../HevSocks5Tunnel.xcframework`)
- Gradle iOS framework:
  ```bash
  # Apple Silicon simulator
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
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

Build (Apple Silicon simulator, no signing):

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
```

The current UI simulator target is `iosSimulatorArm64` (Apple Silicon Mac). It links the
universal `ios-arm64_x86_64-simulator` xcframework slice. An Intel preview requires an
isolated compatibility build using an older Compose release; it is not a production build.

**Compose iOS requirement**: `Info.plist` must include `CADisableMinimumFrameDurationOnPhone = true`
(Compose Multiplatform 1.7+ crashes without it). Room KSP must run for every iOS target
(`kspIosX64` when using Intel simulator).

## Native glass navigation

The iOS floating controls use `UIGlassEffect` on iOS 26 and system-material blur on older
systems. The foreground is hosted in a transparent Compose controller inside the native
material's `contentView`. Pass theme values into that controller rather than forwarding the
parent's entire `CompositionLocalContext`: layout, graphics, and shared-transition scopes
belong to one Compose owner. Sharing bounds across those owners throws
`layouts are not part of the same hierarchy` when opening subscriptions.

Use the system capsule `cornerConfiguration` for Liquid Glass. Keep the material unclipped
so its optical edges can draw, and clip only the foreground. Reserve a transparent
24-point outset in the native host because Compose clips interop views to rectangular
bounds; a host sized exactly to the capsule cuts the system shadow into square corners.
Floating controls use interactive clear glass, with regular glass for the selected lens. The iOS bottom fade is omitted
so page content can remain visible underneath the glass. The selected tab uses a native
material lens, not an opaque painted highlight.

For regression checks, repeatedly switch Config/Home, open subscriptions from the floating
entry, and navigate back. Verify all icons and selected labels remain visible, the lens
follows the selection, and navigation does not crash. Verify translucency with content
behind the bar as well as the empty screens.

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
