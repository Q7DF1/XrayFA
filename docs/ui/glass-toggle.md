# Shared glass toggle

`SharedGlassToggle` is the controlled settings switch in `:shared`. Callers supply
`checked`, `onCheckedChange`, `enabled`, and a localized `contentDescription`.
The control does not persist state or invoke platform business logic.
`SharedSettingsSwitchRow` uses its existing localized title as the label.
Android-only dynamic notifications and hex tun settings delegate their resource
IDs through this shared row; routing preset switches use the same toggle.

## Platforms

- Android: uses the existing Apache-2.0 Backdrop dependency (2.0.1). A local
  track layer and a compressed track sample supply the pixels for thumb blur and refraction, independent
  of the root navigation backdrop. Click and horizontal drag emit the proposed
  state; subsequent rendering follows the caller's state. Press, drag, and state
  transitions reveal a transparent, refracting capsule with chromatic aberration,
  highlights, inner shadow, and spring expansion. The toggle disables the default
  rectangular ripple; its glass response provides interaction feedback. Logical positioning
  and drag direction support RTL. The hit area is 68 × 48 dp and exposes
  `Role.Switch`, checked state, label, and enabled state. Android versions without
  RuntimeShader support use Material 3 Switch.
- iOS: embeds UIKit `UISwitch` with native accessibility enabled. The control
  retains its intrinsic size, centered in a 64 × 48 dp container. The latest
  callback is used without recreating the native view; programmatic state updates
  do not dispatch a value-change event. iOS 26 with the iOS 26 SDK supplies the
  system Liquid Glass interaction appearance; earlier systems retain their
  native switch. UIKit controls system accessibility and interaction behavior.

No new dependency, private Apple API, or additional settings persistence path.

References:
- https://github.com/Kyant0/AndroidLiquidGlass (Backdrop primitives and LiquidToggle example)
- https://developer.apple.com/videos/play/wwdc2025/284/ (native switch design and intrinsic sizing)

## Validation

Compile both implementations:

```sh
./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64
```

Device checks: tap switch and row; drag both directions; change state externally;
verify disabled rows reject input; check dark/light themes and RTL; use TalkBack
and VoiceOver to confirm label, value, and interaction. Check Android below API 33
for Material fallback and iOS below 26 for native fallback. Judge glass appearance
on a release build. Compilation does not substitute for these visual/device checks.

### Android revision verification (2026-10-10)

`:shared:compileDebugKotlinAndroid` and `:androidApp:assembleDebug` passed.
Installed the arm64 debug APK on the connected Samsung SM-S9310. Captured both
held and horizontal-drag frames: the expanded transparent capsule reveals the
track through the refracting outline, with highlights and chromatic edges; the
rectangular toggle ripple is absent. Confirmed boot-on-start returned to its
original off state after testing. User acceptance and release smoothness checks
remain pending; iOS acceptance is deferred until Android is accepted.

### iOS revision verification (2026-10-10)

Built and installed the exact shared toggle API, settings integration, and UIKit
implementation on the booted iPhone 16 / iOS 26.0 simulator with Xcode 26.0.1.
Because the host is Intel and production Compose 1.12 does not ship iosX64 UI
artifacts, runtime checks used the existing isolated `/tmp/xrayfa-ios-intel-preview`
copy with Compose 1.10.3. Production dependencies were not changed. Production
`iosSimulatorArm64` compilation passed earlier; these runtime results do not
replace an Apple Silicon production build or a real-device check.

`GlassToggleTests.testNativeToggleTapDragAndPersistence` passed with zero failures:
- Native switch tap propagates to shared state.
- Both drag directions change state.
- Disabled Agent switch is exposed as disabled.
- Native switch retains an intrinsic frame larger than 40 × 25 points.
- State survives application termination and relaunch.
- The test restores the original IPv6 value.

Inspected recorded held/dragging frames: UIKit shows a transparent enlarged thumb,
track refraction, colored optical edges, and its native soft shadow. The inspected
frames show no rectangular ripple or obvious clipping. Idle state returns to the
normal system switch. VoiceOver, older iOS runtime fallback, dark theme, and real
hardware performance were not exercised in this run.

Artifacts for this local verification:
- `/tmp/xray-toggle-ios-results3.xcresult` (passing XCTest results)
- `/tmp/xray-toggle-ios2.mp4` (native interaction recording)
- `/tmp/xray-ios-frame-017.png` (held native glass frame)

### Android settings coverage follow-up

Android's `SettingsCheckBox` adapter now delegates to `SharedSettingsSwitchRow`,
covering dynamic notifications and hex tun; route preset controls also use
`SharedGlassToggle`. `:androidApp:assembleDebug` and
`:shared:compileKotlinIosSimulatorArm64` passed. Installed the updated APK on
SM-S9310 and confirmed both Android-only settings show the shared capsule control;
a held dynamic-notification frame shows transparent refraction and highlights.
Notification was restored to off and hex tun was confirmed on, their initial values.
