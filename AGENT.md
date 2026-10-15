# AGENT.md

> Context and conventions for AI coding assistants working in **XrayFA**. Humans start with `README.md`. Keep this file in the same PR as any change that affects build, modules, versions, CI, or conventions (see §11).

---

## 1. Project Overview

**XrayFA** is a **Kotlin Multiplatform VPN/proxy client** for **Android and iOS**, built on [Xray-core](https://github.com/XTLS/Xray-core). Protocols: VLESS, VMess, Shadowsocks, Trojan, SOCKS, HTTP, Hysteria2, and others.

- **UI**: Compose Multiplatform shared `RootContent` (Decompose Config\|Home pager + overlays). Android supplies `AndroidPlatformRootHooks`; iOS supplies `IosPlatformRootHooks` (in-development placeholders for remaining gaps — see `docs/IOS_STUBS.md`).
- **Logic**: Decompose components + Koin 4.0.1 (not Dagger)
- **Data**: Room KMP + DataStore KMP; repositories in `:core:data`
- **VPN**: Android `VpnService`; iOS Network Extension (`NEPacketTunnelProvider`)
- **Core**: Go + gomobile → `libv2ray.aar` / `LibXrayLite.xcframework`
- **TUN**: C `hev-socks5-tunnel` (Android JNI; iOS xcframework)
- **Distribution**: GitHub Releases, F-Droid (`com.android.xrayfa`). Google Play is not planned (`APPLICATION_ID_PLAY` kept but unused)
- **License**: Apache-2.0
- **Version**: `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` (currently 1.7.1 / 35)

Product rule: **Android is the reference; iOS aligns to Android.** Do not add a second parallel implementation of a screen.

**KMP migration is complete.** New features go in `:shared` + `PlatformRootHooks` only. Track remaining iOS gaps in `docs/IOS_STUBS.md`.

---

## 2. Prerequisites

| Tool | Source of truth |
|------|-----------------|
| JDK | 11 bytecode / **17** to run Gradle (CI uses Temurin 17) |
| Android SDK | compileSdk **36**, minSdk **28**, targetSdk **36** |
| NDK | **28.2.13676358** (CI: r28c) for `:tun2socks` |
| Go | `AndroidLibXrayLite/go.mod` |
| gomobile / gobind | `go install golang.org/x/mobile/cmd/{gomobile,gobind}@latest` |
| Gradle | Wrapper **8.11.1** — always `./gradlew` |
| Xcode | Needed for `iosApp` / Network Extension |

Catalog versions live **only** in `gradle/libs.versions.toml` (Kotlin **2.1.10**, KSP **2.1.10-1.0.31**, AGP **8.10.0**, Compose BOM **2026.03.00**, CMP **1.7.3**, Room **2.7.0**, Koin **4.0.1**, Decompose **3.2.2**). Do not hardcode versions in `build.gradle.kts` (LeakCanary in `:androidApp` debug is the listed exception).

---

## 3. Build & Run

### 3.1 Clone

```bash
git clone --recursive <repo-url>
cd XrayFA
git submodule update --init --recursive   # if already cloned
```

Submodules: `AndroidLibXrayLite/` (Xray-core gomobile), `tun2socks/src/main/jni/hev-socks5-tunnel/`.

### 3.2 Android native core (`libv2ray.aar`)

`:androidApp` needs `androidApp/libs/libv2ray.aar` (gitignored). Generate once:

```bash
./gradlew copyXrayLib
# or: cd AndroidLibXrayLite && gomobile bind … && cp libv2ray.aar ../androidApp/libs/
```

`preBuild` does **not** depend on `copyXrayLib` (intentional).

F-Droid / GitHub Release native prep is `scripts/fdroid.sh` (`prebuild` then `bind`). The fdroiddata recipe in `fdroid/builds.yml` only calls that script — geo files, Go bootstrap, gomobile, and ABI pinning live here, not in fdroiddata.

### 3.3 iOS native core (`LibXrayLite.xcframework`)

Required before `:shared` / `:core:native-bridge` iOS compile:

```bash
./scripts/build_libxray_ios.sh
```

Output: `AndroidLibXrayLite/LibXrayLite.xcframework` (gitignored). CI caches it in `ios-shared.yml`.

The iOS host weak-links UIKit in `iosApp/iosApp.xcconfig`: Kotlin/Native class imports otherwise prevent pre-iOS-26 launch before runtime availability guards execute. Keep iOS 26 API calls behind version checks.

### 3.4 Gradle

```bash
./gradlew assembleDebug
./gradlew assembleRelease          # minify + shrink; unsigned if no keystore
./gradlew :androidApp:compileDebugKotlin
./gradlew :shared:compileDebugKotlin
```

KMP `iosArm64` / `iosSimulatorArm64` / `iosX64` targets are registered **only on macOS** (Linux / F-Droid skip Kotlin/Native). Force with `-PenableIosTargets=true|false`.

- Signing: `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` + `androidApp/xrayfa.jks`
- Override applicationId with `-PAPPLICATION_ID=`
- Debug APK is **debuggable**, no R8, and includes **LeakCanary** — expect jank vs release

### 3.5 Windows

`:tun2socks` runs `fix_headers.bat` before native `preBuild`. Run it in the repo root if header includes fail.

---

## 4. Testing

```bash
./gradlew :common:testDebugUnitTest
./gradlew :domain:testDebugUnitTest          # parser goldens + kotlinx JSON + Agent catalog
./gradlew :core:datastore:testDebugUnitTest
./gradlew :domain:iosX64Test                 # Native stand-in on Intel Macs
./gradlew :domain:iosSimulatorArm64Test      # Apple Silicon simulator
./gradlew test                               # JVM/Android unit tests (needs aar for some modules)
```

- Shared business logic belongs in **`commonTest`**, not `androidUnitTest`. Gson parity stays on Android (Gson is JVM-only).
- Parser / config goldens: `domain/src/commonTest/kotlin/com/android/xrayfa/parser/` (`ProtocolParserGoldenTest`, `AbstractConfigParserGoldenTest`).
- Agent catalog: `domain/src/commonTest/kotlin/com/android/xrayfa/agent/XrayAgentCatalogTest.kt` (node/subscription summaries must not leak URLs or node JSON).
- Native delay mapping: `core/native-bridge/.../DecodeNativeDelayMsTest.kt`.
- GeoLite country flags: `common/src/commonTest/.../CountryFlagEmojiTest.kt`, `GeoIpCountryDisplayTest.kt`, `MmdbCountryLookupTest.kt` (MaxMind `GeoIP2-Country-Test.mmdb` fixture in `androidUnitTest/resources`).
- GeoLite download: `common/src/commonTest/.../GeoLiteInstallerTest.kt`. The iOS settings download button is disabled (`geoLiteDownloadSupported` = false; the host cannot reach SOCKS inside the Network Extension); Android uses its local SOCKS endpoint.
- Delay probe (home live vs outbound fallback): `common/src/commonTest/.../DelayProbeTest.kt`.
- Digest (SHA-256 / MD5): `common/src/commonTest/.../DigestCalculatorTest.kt` (JVM + `:common:iosX64Test`).
- New parser / routing / subscription logic: add a `commonTest` golden (share link → kotlinx JSON) **before** changing the encoder.

`./gradlew allTests` (including iOS simulator) is the full KMP bar; CI currently runs the JVM subset on `feat/**` (see §8).

Android node editor interaction tests live in `androidApp/src/androidTest/java/com/android/xrayfa/NodeEditorInteractionTest.kt`. Build the app and test APK with `-PAPPLICATION_ID=com.android.xrayfa.nodetest` and run that class with AndroidJUnitRunner on a device or emulator. The tests require the isolated application ID, verify dropdown interaction, protocol draft retention, validation, outbound preview, and database round-trip, and clean up their own saved node.

iOS glass UI regression tests live in `iosApp/ui-tests` (independent XcodeGen XCTest target). With the app installed on an iOS 26 simulator, run the `GlassUITests` scheme; `GlassToggleTests` verifies native toggle tap, bidirectional drag, disabled state, and persistence across relaunch. Record interactions for visual inspection; see `iosApp/README.md` for the Intel preview limitation.

---

## 5. Directory Structure

```
XrayFA/
├── androidApp/          # Android application: Activity, VpnService, Agent facade, thin UI wrappers
├── iosApp/              # Xcode app + PacketTunnel Network Extension
├── scripts/fdroid.sh    # F-Droid + GitHub Release geo/gomobile/ABI prep
├── fdroid/builds.yml    # fdroiddata Builds snippet (no `subdir: app`)
├── shared/              # CMP UI + Decompose + Koin modules → XrayFAShared.framework
│   └── src/commonMain/composeResources/   # en / zh-rCN / ko / ru-rRU strings
├── common/              # Kernel types: RoutingMode, DomainStrategy, Rule JSON, AppJson, logging
├── domain/              # Parsers, Xray JSON models, protocol DTOs, Agent facade (no :core / :platform deps)
├── core/database/       # Room KMP
├── core/datastore/      # DataStore KMP
├── core/data/           # RoomNodeRepository, KmpSubscriptionRepository, EntityMappers
├── core/network/        # Ktor
├── core/native-bridge/  # gomobile expect/actual (needs xcframework on iOS)
├── platform/vpn/        # VPN / TUN expect/actual
├── tun2socks/           # Android JNI TUN
├── AndroidLibXrayLite/  # submodule
├── gradle/libs.versions.toml
├── docs/                # Translated READMEs, screenshots and iOS gaps
└── .github/workflows/
```

### Module graph (simplified)

```
:androidApp → :shared → :domain → :common
            → :core:data → :core:database / :core:datastore
            → :platform:vpn / :core:native-bridge / :tun2socks
:iosApp (Xcode) → XrayFAShared.framework (:shared)
```

`:domain` must **not** depend on `:core:*` or `:platform:*`. Repositories do **not** live in `:shared`.

---

## 6. Runtime Data Flow

1. Share link / subscription → `:domain` parser → Xray JSON (`XrayConfiguration`).
2. Selected node + settings → encoder (`AndroidXrayConfigEncoder` / iOS encoder) → core config.
3. Android: `XrayBaseService` (`VpnService`) + `tun2socks` TUN → local SOCKS; `XrayCoreManager` starts libv2ray.
4. Traffic speeds: one `queryAllOutboundTrafficStats()` snapshot per poll (`tag,direction,value;…`), then parse proxy uplink/downlink. Do **not** call the removed `QueryStats(tag, direct)` API — the pinned `AndroidLibXrayLite` SHA no longer exports it (CI rebuilds the AAR from the submodule).
5. iOS: Network Extension starts LibXrayLite + HevSocks5Tunnel; App Group for shared settings.
6. Local full Xray JSON: shared JSON editor → `Node(protocolPrefix="json-config", jsonData=original text)` with persistent inbound tag in its internal identifier. `JsonVpnConfig` prepares a separate JSON tree for the platform VPN inlet, virtual DNS channel, and required stats manager; ordinary links keep their existing parsers and settings. Android passes only the record ID to the service; iOS transfers JSON and SOCKS metadata in one versioned App Group envelope. JSON records skip link sharing, delay tests, fixed-tag traffic statistics, and application subscription chains. No Room schema migration.

UI: Android `MainActivity` → `AndroidAppShell` → shared `RootContent` (Decompose `childPages` pager: Config | Home) plus `AndroidPlatformRootHooks` for VPN / CameraX QR / geo import / per-app picker / logcat / share / bug report. Secondary destinations (settings, subscriptions, QR, apps, logcat, route settings, node edit) are **not** tabs: they are a Decompose `childStack` over the pager, with `RootStackConfig.Idle` as the empty sentinel, so they get Material push/pop motion. `RootComponent` stays verb-based (`openSettings()`, `openNodeEdit(id)`, `navigateBack()`); callers never push configs themselves. On Android the system gesture drives the root `BackDispatcher` (`enableOnBackInvokedCallback=true` + `PredictiveBackHandler` in `AndroidAppShell`) and `RootContent` renders `predictiveBackAnimation`; `handleBackButton` is off on `childStack` so the pop is not doubled. iOS uses the same `RootContent` with `IosPlatformRootHooks` (ShareNode / BugReport are real; remaining Android-only slots show in-development placeholders) and plain slide animation — iOS has no system back handler, so stack screens pop through their in-screen back affordance. List screens (settings, config, subscriptions, apps, logcat, route) share `SharedListScaffold` (`LargeTopAppBar` + `exitUntilCollapsed`); Config and Apps search uses a Material3 `SearchBar` with a 300 ms debounce. Labels come from `remember*UiLabels()` (`compose-resources`), not hardcoded English defaults.

Android launcher Start/Stop shortcuts invoke `XrayViewmodel` service operations directly, without waiting for `AndroidAppShell`. Start requests VPN consent through the Activity Result API when needed and connects only after approval; shortcut extras are consumed once. Config resolution and missing-config feedback belong to `XrayBaseServiceManager`. For other external actions, `AndroidRootActionCoordinator.collectActions` processes requests sequentially: clearing a consumed request must not cancel suspended action handling (do not use `collectLatest` here).

---

The shared manual node editor exposes only fields implemented by its share-link encoding path. Protocol defaults, transport/security choices, advanced parameter keys, and structural validation live in `shared/config/NodeFormOptions.kt` and `NodeFormValidation.kt`. Outbound previews use `OutboundObjectSerializer`, the runtime serializer. Keep the editor toolbar pinned while scrolling so Back and Save remain accessible, and clear input focus before opening pickers or previews. Keep endpoint summaries (address and country) synchronized when updating node URLs. Verify new editor fields in `NodeFormTransportTest` against serialized outbound JSON, and check core compatibility against the version in `AndroidLibXrayLite/go.mod`.

## 7. Code Style & Conventions

- Kotlin official style; 4-space indent; match surrounding code. No ktlint/detekt.
- **Documentation**: Keep only necessary documents in the repository, in English. Other-language versions may be used locally or shared outside the repository, but must not be committed. Update existing documentation rather than adding redundant plans, handovers, or indexes.
- **Versions**: only `gradle/libs.versions.toml` + `gradle.properties` for app version.
- **DI**: Koin modules (`androidKoinModules()`, `appAgentDiModule`, `sharedServicesDiModule`, …). `XrayAppCompatFactory` still constructs Android components; new injectables go in Koin, not Dagger.
- **New screens (R-1)**: logic in `:shared`; Android is a thin wrapper. Do not create Android+Shared parallel UIs. Self-check: `rg -l 'com.android.xrayfa.shared.ui' androidApp/src/main/java` should only grow.
- **No duplicate common code (R-2)**: after adding `commonMain`, delete the `androidApp` copy.
- **iOS stubs (R-3)**: no `TODO()`/`error()`/`return false` actuals without listing them in `docs/IOS_STUBS.md` and keeping the gap marked as unresolved.
- **i18n (R-9)**: UI copy lives in `shared/.../composeResources/values*/strings.xml` (4 locales). New `UiLabels` fields must get a string key in the same PR. Android `res/values/strings.xml` remains for Manifest / notifications / non-Compose.
- **ProGuard**: Release minify is on. After Koin/Decompose/serialization/JNI changes, run `assembleRelease` and smoke-test. Lint `Instantiatable` is disabled: components are created by `XrayAppCompatFactory`, not a default ctor.
- **Submodules**: do not edit upstream trees unless the task says so; do not pin unpublished SHAs. Bind `libv2ray.aar` / xcframework from the **pinned** SHA (`git submodule update --init --recursive`) so Kotlin/Swift match CI.
- Comments: intent/trade-offs only.

---

## 8. CI & Release

| Workflow | Trigger | What it actually runs |
|----------|---------|------------------------|
| `kmp-unit-tests.yml` | push/PR `main` + `feat/**` | `:common` / `:domain` / `:core:datastore` `testDebugUnitTest` + `:core:data:compileDebugKotlinAndroid` |
| `ios-shared.yml` | push/PR `main` + `feat/**` | `:domain:iosSimulatorArm64Test`; cache/build xcframework; `:shared:compileKotlinIosSimulatorArm64` |
| `android.yml` | push `main` / tag `v*` / PR to `main` | NDK + gomobile + `./gradlew test` + `assembleRelease` + GitHub Release on tag. **Does not run on `feat/**`** |
| `google-play.yml` | unused | Play variant kept disabled |
| `update_submodules.yaml` | cron | Submodule bump PR |

F-Droid: `dependenciesInfo.includeInApk/Bundle = false`. Debian builders skip iOS KMP targets (no konan download). Recipe: `fdroid/builds.yml` → fdroiddata (no `subdir`; `scripts/fdroid.sh` does geo / gomobile / ABI). Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## 9. Security & Gotchas

- Never commit `androidApp/xrayfa.jks`, `local.properties`, `.env`, entitlements hacks, `androidApp/libs/*.aar`, `LibXrayLite.xcframework`, `.kotlin/`.
- Do not commit unpublished `AndroidLibXrayLite` SHAs.
- Test fixtures must not assign string literals to `*password*` fields (GitGuardian Generic Password). Use a named dummy constant; `.gitguardian.yaml` ignores test/golden paths.
- Debug vs release: LeakCanary + `debuggable` make debug **much** slower on device; judge UI smoothness on release / profileable.
- This is a legitimate proxy client — do not weaken privacy defaults in parsers/routing.

---

## 10. Pull Requests

- Branch: `<type>/<short-description>` (`feat/migrateToKMP`, `fix/tun-mtu-crash`, …)
- Commits: Conventional Commits. Choose a scope matching the affected module or feature.
- Small, focused PRs. Squash-merge to `main`.
- DoD: compile the modules you touched; `commonTest` for parser/config; no secrets/artifacts; **update this file** if §1–§9 changed.

---

## 11. Keeping This Document Up to Date

| If you change… | Update |
|----------------|--------|
| Toolchain / catalog versions | §2 |
| Build / gomobile / xcframework | §3 |
| Tests | §4 |
| Modules | §5 |
| VPN / encoder / UI shell / libv2ray APIs | §6 |
| DI, i18n, layering | §7 |
| Workflows | §8 |
| Secrets / artifacts / GitGuardian | §9 |

Verify numbers against `gradle/libs.versions.toml`, `gradle.properties`, `go.mod`, and the workflow YAML — not memory.

---

## 12. Reference Docs

- `CONTRIBUTING.md` — contributor workflow
- `README.md` / `docs/README_zh-CN.md` / `docs/README_RU.md` / `docs/README_KR.md` — user and build guides
- `iosApp/README.md` — iOS build and UI tests
- `docs/IOS_STUBS.md` — intentional iOS stubs and remaining platform gaps
