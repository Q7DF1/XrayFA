# AGENT.md

> Context and conventions for AI coding assistants working in **XrayFA**. Humans start with `README.md`. Keep this file in the same PR as any change that affects build, modules, versions, CI, or conventions (see §11).

---

## 1. Project Overview

**XrayFA** is a **Kotlin Multiplatform VPN/proxy client** for **Android and iOS**, built on [Xray-core](https://github.com/XTLS/Xray-core). Protocols: VLESS, VMess, Shadowsocks, Trojan, SOCKS, HTTP, Hysteria2, and others.

- **UI**: Compose Multiplatform (shared screens) + Jetpack Compose on Android (Navigation3 shell) + SwiftUI/Compose iOS shell
- **Logic**: Decompose components + Koin 4.0.1 (not Dagger)
- **Data**: Room KMP + DataStore KMP; repositories in `:core:data`
- **VPN**: Android `VpnService`; iOS Network Extension (`NEPacketTunnelProvider`)
- **Core**: Go + gomobile → `libv2ray.aar` / `LibXrayLite.xcframework`
- **TUN**: C `hev-socks5-tunnel` (Android JNI; iOS xcframework)
- **Distribution**: GitHub Releases, F-Droid (`com.android.xrayfa`). Google Play is not planned (`APPLICATION_ID_PLAY` kept but unused)
- **License**: Apache-2.0
- **Version**: `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` (currently 1.6.4 / 33)

Product rule: **Android is the reference; iOS aligns to Android.** Do not add a second parallel implementation of a screen.

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

### 3.3 iOS native core (`LibXrayLite.xcframework`)

Required before `:shared` / `:core:native-bridge` iOS compile:

```bash
./scripts/build_libxray_ios.sh
```

Output: `AndroidLibXrayLite/LibXrayLite.xcframework` (gitignored). CI caches it in `ios-shared.yml`.

### 3.4 Gradle

```bash
./gradlew assembleDebug
./gradlew assembleRelease          # minify + shrink; unsigned if no keystore
./gradlew :androidApp:compileDebugKotlin
./gradlew :shared:compileDebugKotlin
```

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
- GeoLite download: `common/src/commonTest/.../GeoLiteInstallerTest.kt`.
- New parser / routing / subscription logic: add a `commonTest` golden (share link → kotlinx JSON) **before** changing the encoder.

`./gradlew allTests` (including iOS simulator) is the full KMP bar; CI currently runs the JVM subset on `feat/**` (see §8).

---

## 5. Directory Structure

```
XrayFA/
├── androidApp/          # Android application: Activity, VpnService, Agent facade, thin UI wrappers
├── iosApp/              # Xcode app + PacketTunnel Network Extension
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
├── docs/                # Migration plan + STEP handovers
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
4. iOS: Network Extension starts LibXrayLite + HevSocks5Tunnel; App Group for shared settings.

UI: Android `MainActivity` → `XrayFAContainer` (Navigation3 + Pager) wrapping shared composables. iOS uses `RootContent` + Decompose. Labels come from `remember*UiLabels()` (`compose-resources`), not hardcoded English defaults.

---

## 7. Code Style & Conventions

- Kotlin official style; 4-space indent; match surrounding code. No ktlint/detekt.
- **Versions**: only `gradle/libs.versions.toml` + `gradle.properties` for app version.
- **DI**: Koin modules (`androidKoinModules()`, `appAgentDiModule`, `sharedServicesDiModule`, …). `XrayAppCompatFactory` still constructs Android components; new injectables go in Koin, not Dagger.
- **New screens (R-1)**: logic in `:shared`; Android is a thin wrapper. Do not create Android+Shared parallel UIs. Self-check: `rg -l 'com.android.xrayfa.shared.ui' androidApp/src/main/java` should only grow.
- **No duplicate common code (R-2)**: after adding `commonMain`, delete the `androidApp` copy.
- **iOS stubs (R-3)**: no `TODO()`/`error()`/`return false` actuals without listing them in `docs/IOS_STUBS.md` and leaving the Stage unchecked.
- **i18n (R-9)**: UI copy lives in `shared/.../composeResources/values*/strings.xml` (4 locales). New `UiLabels` fields must get a string key in the same PR. Android `res/values/strings.xml` remains for Manifest / notifications / non-Compose.
- **ProGuard**: Release minify is on. After Koin/Decompose/serialization/JNI changes, run `assembleRelease` and smoke-test.
- **Submodules**: do not edit upstream trees unless the task says so; do not pin unpublished SHAs.
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

F-Droid: `dependenciesInfo.includeInApk/Bundle = false`. Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## 9. Security & Gotchas

- Never commit `androidApp/xrayfa.jks`, `local.properties`, `.env`, entitlements hacks, `androidApp/libs/*.aar`, `LibXrayLite.xcframework`, `.kotlin/`.
- Do not commit unpublished `AndroidLibXrayLite` SHAs.
- Debug vs release: LeakCanary + `debuggable` make debug **much** slower on device; judge UI smoothness on release / profileable.
- This is a legitimate proxy client — do not weaken privacy defaults in parsers/routing.

---

## 10. Pull Requests

- Branch: `<type>/<short-description>` (`feat/migrateToKMP`, `fix/tun-mtu-crash`, …)
- Commits: Conventional Commits. `refactor(kmp):` is the usual scope for this migration.
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
| VPN / encoder / UI shell | §6 |
| DI, i18n, layering | §7 |
| Workflows | §8 |
| Secrets / artifacts | §9 |

Verify numbers against `gradle/libs.versions.toml`, `gradle.properties`, `go.mod`, and the workflow YAML — not memory.

---

## 12. Reference Docs

- `README.md` / `README_zh-CN.md` / `README_RU.md` / `README_KR.md`
- `docs/KMP_MIGRATION_PLAN.md` — live step table (73+)
- `docs/KMP_MIGRATION_STEP93_HANDOVER.md` — Phase 7 A1 Agent 契约
- `docs/KMP_MIGRATION_STEP94_HANDOVER.md` — Phase 7 A2 Android Facade + Koin
- `docs/KMP_MIGRATION_STEP95_HANDOVER.md` — Phase 7 A3 Agent 总开关（默认关）
- `docs/KMP_MIGRATION_STEP96_HANDOVER.md` — Phase 7 A4 AppFunctions Phase A 只读
- `docs/KMP_MIGRATION_STEP97_HANDOVER.md` — Phase 7 A5 API 36 adb 手测
- `docs/KMP_MIGRATION_STEP98_HANDOVER.md` — Phase 7 B1+B2 写操作 + OS enable 同步
- `docs/KMP_MIGRATION_STEP99_HANDOVER.md` — iOS 主题跟随设置 `darkMode`
- `docs/KMP_MIGRATION_STEP100_HANDOVER.md` — iOS `measureOutboundDelay` ObjC shim
- `docs/KMP_MIGRATION_STEP101_HANDOVER.md` — iOS GeoIP（common MMDB reader + 国旗 emoji）
- `docs/KMP_MIGRATION_STEP102_HANDOVER.md` — 共享设置 GeoLite 下载 + `geoLiteInstall`
- `docs/ANDROID_AGENT_APPFUNCTIONS_PLAN.md` — **Android-only** Agent 可控能力（AppFunctions 接口与分阶段实施）
- `docs/IOS_PLATFORM_GUIDE.md`, `docs/DEPENDENCY_MIGRATION_GUIDE.md`
- `docs/KMP_MIGRATION_MIDTERM_REVIEW.md` — rules R-1…R-10 (local notes; may be untracked)
- Domain models: look in `:domain`, not `androidApp/.../model/` (that tree is gone)
