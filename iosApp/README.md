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

On iOS 26, action controls use UIKit
`UIButtonConfiguration.glassButtonConfiguration()` and native SF Symbols.
On iOS 26, the floating navigation is a native `UITabBar`: UIKit owns the
selection lens, press deformation, dragging, refraction, symbols and labels.
Compose passes localized items, selection and callbacks; it does not animate a
replacement glass capsule. Older iOS versions retain the custom material fallback.

A transparent interop anchor controls placement and lifetime. The tab bar is
attached to that anchor's owning window to receive the full UIKit layout context
and the live page backdrop. Removing the anchor removes the native bar, including
when opening subscriptions. Attach it only after the anchor has nonzero layout
bounds: an initial zero-sized layout makes UIKit cache compact item metrics.
Both platforms use a 62-point/dp visible capsule and action button, a 12-point/dp
inter-control gap, and an 8-point/dp bottom margin outside the safe area. Each tab receives
90 points/dp plus 4 points/dp of capsule padding on each side (188 for two tabs),
clamped to the available width. This compact geometry matches UIKit's native
floating tabs. The native host compensates for its horizontal optical insets without
scaling the glass, symbols or fonts.

The shared action button accepts an optional `nativeSystemImage` for reusable iOS
system controls. Android retains its existing vector and glass implementation.
Older iOS versions and custom icons without a system symbol retain the material
fallback. Only styling locals are passed to its Compose foreground: sharing layout
or transition scopes across Compose owners causes `layouts are not part of the
same hierarchy` when opening subscriptions.

Reserve a transparent 24-point outset around native controls because Compose clips
interop views to rectangular bounds. Without it, the system shadow gets cut into
square corners. On iOS, home scrolling extends beneath the navigation, with bottom
clearance applied to scroll content rather than shortening the viewport. Do not
add opaque backgrounds or a gradient fade behind the glass.

For regression checks, use the independent XCTest UI target in `ui-tests` after
installing the app on an iOS 26 simulator. Generate its project with XcodeGen and
run the `GlassUITests` scheme. It performs real press-and-drag gestures in both
directions, checks selection, opens subscriptions, verifies removal of the native
bar, then returns and verifies restoration. Record the simulator during the test
and inspect frames while the finger is held and moving; static screenshots and
programmatic selection alone do not validate Liquid Glass interaction.

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
