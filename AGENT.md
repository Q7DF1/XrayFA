# AGENT.md

> This file provides AI coding assistants (Cursor, Claude Code, Copilot, etc.) with the context and conventions needed to work effectively and safely in the **XrayFA** repository. It follows the [agents.md](https://agents.md) convention. Human contributors should start with `README.md`.

> **Keep this document up to date.** `AGENT.md` is a living document. Whenever a change affects anything described here — build steps, tooling/versions, module layout, dependencies, CI, conventions, or distribution channels — update the relevant section **in the same PR** so the doc never goes stale. See Section 11 for details.

---

## 1. Project Overview

**XrayFA** is an **Android VPN proxy client** built on top of [Xray-core](https://github.com/XTLS/Xray-core), supporting VLESS, VMess, Shadowsocks, Trojan, SOCKS, HTTP, Hysteria2, and other protocols.

- **Languages/Platform**: Kotlin (Jetpack Compose UI) + Go (gomobile proxy core) + C (NDK/JNI TUN tunnel)
- **Architecture**: MVVM + Repository, dependency injection via Dagger 2
- **Distribution**: GitHub Releases, F-Droid (`com.android.xrayfa`); ~~Google Play (`com.q7df1.xrayfa`)~~ _(no Play Store release planned for now; related config is kept but disabled)_
- **License**: Apache-2.0
- **Current version**: `1.6.3` (versionCode `32`)

### Technology Layers

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 + Navigation3 (beta) + Adaptive Layout |
| App logic | Kotlin, Coroutines, ViewModel, Room, DataStore, Dagger 2 (KSP) |
| VPN | `VpnService` (`XrayBaseService`) |
| Proxy core | Go + gomobile → `libv2ray.aar` (wraps Xray-core) |
| TUN forwarding | C (`hev-socks5-tunnel`) + `ndk-build` JNI |

---

## 2. Prerequisites

Before running any build, make sure the following toolchain is ready:

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 11 (compile target) / 17 (CI) | `compileOptions` targets Java 11 |
| Android SDK | compileSdk **36**, minSdk **28**, targetSdk **36** | |
| Android NDK | **28.2.13676358** (CI uses r28c) | Required to build the `tun2socks` native library |
| Go | **1.26+** (`go.mod` is authoritative) | Required to build `libv2ray.aar` |
| gomobile / gobind | `@latest` | `go install golang.org/x/mobile/cmd/{gomobile,gobind}@latest` |
| Gradle | 8.11.1 (wrapper included) | Use `./gradlew`; do not install globally |

Key versions are centralized in `gradle/libs.versions.toml` (AGP 8.10.0, Kotlin 2.0.21, KSP 2.0.21-1.0.27, Compose BOM 2026.03.00, Dagger 2.57.1, Room 2.7.0, OkHttp 4.12.0).

---

## 3. Build & Run

### 3.1 First clone (submodules must be fetched recursively)

```bash
git clone --recursive <repo-url>
cd XrayFA
# If already cloned but submodules are missing:
git submodule update --init --recursive
```

This repository contains two git submodules (see `.gitmodules`):
- `AndroidLibXrayLite/` — gomobile bindings for Xray-core
- `tun2socks/src/main/jni/hev-socks5-tunnel/` — the TUN→SOCKS5 native implementation

### 3.2 Build `libv2ray.aar` (prerequisite for the Gradle build)

`:app` depends on `app/libs/*.aar`. This AAR is **not checked into the repo** and must be generated from Go sources first:

```bash
cd AndroidLibXrayLite
gomobile init
go mod tidy -v
gomobile bind -v -trimpath -androidapi 21 \
  -ldflags="-s -w -buildid= -checklinkname=0" ./
mkdir -p ../app/libs && cp libv2ray.aar ../app/libs/
```

> You can also use the built-in Gradle task `./gradlew copyXrayLib` (which chains `initGoMobile → goMod → bindXrayLib → copyXrayLib`). Note: the `preBuild` dependency on `copyXrayLib` is **intentionally commented out** in `app/build.gradle.kts`, so local development requires running it once manually.

### 3.3 Gradle build commands

```bash
./gradlew assembleDebug     # Debug APK
./gradlew assembleRelease   # Release APK (default F-Droid package: com.android.xrayfa)
./gradlew clean             # Clean
```

- Release signing reads the `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` env vars and `app/xrayfa.jks`; if missing, an **unsigned** release APK is built.
- ~~`./gradlew bundleRelease -PAPPLICATION_ID=com.q7df1.xrayfa` — Play Store AAB (different package)~~ _(no Google Play release planned for now; not used)_
- Use `-PAPPLICATION_ID=<id>` to override the applicationId per channel; default is `com.android.xrayfa`.

### 3.4 Windows note

Native builds require fixing the `#include` paths in C headers first. `tun2socks/build.gradle.kts` registers a task that runs `fix_headers.bat` before `preBuild` automatically. If you hit header errors while building the native library manually on Windows, run `fix_headers.bat` in the repo root first.

---

## 4. Testing

```bash
./gradlew test                 # JVM unit tests
./gradlew connectedAndroidTest # Instrumented tests (requires a device/emulator)
```

- Test frameworks: JUnit 4.13.2, AndroidX Test JUnit 1.3.0, Espresso 3.7.0, Compose UI Test.
- **Current test coverage is thin** and mostly consists of Android Studio default templates. The one with real value is `app/src/test/java/com/android/xrayfa/parser/AbstractConfigParserTest.kt` (unit tests for config generation).
- **Convention**: When adding business logic (especially pure logic in `parser/`, `model/`, `utils/`, `repository/`), add unit tests following the style of `AbstractConfigParserTest`.

---

## 5. Directory Structure

```
XrayFA/
├── app/                      # :app main application module (namespace com.android.xrayfa)
│   ├── build.gradle.kts      # Includes gomobile build tasks (buildGoMobile/bindXrayLib/copyXrayLib)
│   ├── libs/                 # Where libv2ray.aar goes (build artifact, not version-controlled)
│   ├── proguard-rules.pro    # R8/ProGuard keep rules
│   └── src/main/java/com/android/xrayfa/
│       ├── core/             # XrayBaseService(VpnService), XrayCoreManager, QuickStartTileService
│       ├── parser/           # Protocol/subscription parsers + ParserFactory
│       ├── model/            # Xray JSON config objects (with stream/ subpackage)
│       ├── dto/              # Node, Subscription, Link, etc. data transfer objects
│       ├── dao/              # Room DAOs + XrayFADatabase (version 4)
│       ├── repository/       # NodeRepository, SubscriptionRepository, etc.
│       ├── viewmodel/        # ViewModels per screen
│       ├── di/               # Dagger modules + XrayFAComponent
│       ├── ui/               # Compose components, navigation, scene, theme
│       ├── helper/ utils/    # Notifications, utilities
│       ├── MainActivity.kt   # Entry Activity
│       ├── XrayFAApplication.kt
│       └── XrayAppCompatFactory.kt  # AppComponentFactory, DI injection for Activity/Service
├── common/                   # :common shared library (Constant, SettingsRepository, Socks generator, DI qualifiers)
├── tun2socks/                # :tun2socks module (TProxyService JNI wrapper + hev-socks5-tunnel submodule)
│   └── src/main/jni/         # Android.mk / Application.mk (ndk-build, no CMake)
├── AndroidLibXrayLite/       # submodule: gomobile bindings for Xray-core (go.mod, gen_assets.sh)
├── gradle/libs.versions.toml # Dependency version catalog (single source of truth)
├── gradle.properties         # VERSION_NAME/CODE, APPLICATION_ID_PLAY, GEO versions, etc.
├── settings.gradle.kts       # Module registration: :app, :tun2socks, :common
├── docs/                     # Internal technical docs (incl. KMP migration plan)
├── fastlane/                 # F-Droid / Play Store metadata (multilingual)
└── .github/workflows/        # CI (android.yml, google-play.yml, update_submodules.yaml)
```

### Module dependencies

```
:app ─→ :tun2socks ─→ :common
  └───→ :common
  └───→ app/libs/libv2ray.aar
```

---

## 6. Runtime Data Flow (to understand the code)

1. User selects a node → `parser/ParserFactory` parses the share link/subscription → generates Xray JSON config via `model/`.
2. `core/XrayBaseService` (`VpnService`) sets up the TUN interface.
3. `tun2socks/TProxyService` starts `hev-socks5-tunnel` over JNI, forwarding TUN traffic to a local SOCKS proxy.
4. `core/XrayCoreManager` starts Xray-core through the `libv2ray` bindings to handle proxying.

Config generation lives in `parser/` + `model/`; app settings are stored in DataStore (`common/SettingsRepository`); nodes/subscriptions are stored in Room (`dao/XrayFADatabase`).

---

## 7. Code Style & Conventions

- **Kotlin style**: `kotlin.code.style=official` (`gradle.properties`). Follow the official Kotlin coding conventions.
- This project has **no** ktlint / detekt / spotless / `.editorconfig` configured; match the naming, indentation (4 spaces), and formatting of surrounding code.
- **Dependency management**: All dependency versions are declared **only** in `gradle/libs.versions.toml` and referenced via `libs.xxx` aliases. **Do not** hardcode versions in `build.gradle.kts` (the `leakcanary` debug dependency in `app/build.gradle.kts` is a pre-existing exception).
- **DI**: When adding an injectable component, register it in the corresponding Dagger module under `di/`; Activities/Services are injected via `XrayAppCompatFactory`.
- **Naming**: Package root is `com.android.xrayfa.*`; config models mirror the Xray JSON structure — consult `app/.../model/README.md` before changing them.
- **ProGuard**: When adding classes accessed via reflection/JNI/serialization, update `app/proguard-rules.pro` accordingly (Release enables `minify` + `shrinkResources`).
- **Comments**: Only add comments to explain non-obvious intent, trade-offs, or constraints; avoid redundant comments that merely restate the code.

---

## 8. CI & Release

CI is defined under `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `android.yml` | push `main` / tag `v*` / PR | JDK 17 + pinned NDK r28c → download geo data → gomobile bind → `assembleRelease` → upload APK; auto GitHub Release on tag |
| ~~`google-play.yml`~~ | ~~`workflow_dispatch`~~ | ~~Builds the Play variant (AAB/APK) using `APPLICATION_ID_PLAY`~~ _(no Google Play release planned; workflow kept but unused)_ |
| `update_submodules.yaml` | daily cron + manual | Updates submodules and opens a PR automatically |

- **F-Droid compatibility**: `dependenciesInfo.includeInApk/Bundle = false` (disables dependency metadata).
- CI reads the Go version from `AndroidLibXrayLite/go.mod` and pins NDK r28c for reproducible builds.
- Required secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## 9. Security & Gotchas (AI assistants MUST follow)

- **Never commit secrets/credentials**: `app/xrayfa.jks`, `local.properties`, keystore passwords, and any `secrets` must never be written to the repo or logs.
- **Submodules are external upstreams**: `AndroidLibXrayLite/` and `hev-socks5-tunnel/` are independent repos. Unless the task explicitly requires it, **do not** modify files inside submodules; make changes on the main-repo side.
- **Do not commit build artifacts**: `app/libs/*.aar`, `build/`, `.gradle/`, etc. are generated and must not be committed.
- **Version bumps**: To change the app version, edit `VERSION_NAME` / `VERSION_CODE` in `gradle.properties`; do not hardcode them in `build.gradle.kts`.
- **Distribution channels**: Currently released only via GitHub Releases and F-Droid (package `com.android.xrayfa`). ~~Google Play (`com.q7df1.xrayfa`)~~ is **not planned**; `APPLICATION_ID_PLAY`, `google-play.yml`, and related config are kept but disabled. If re-enabled later, control it via `-PAPPLICATION_ID`.
- **Networking tool nature**: This is a legitimate open-source proxy/VPN client. Be careful when changing networking, routing, or protocol-parsing logic to avoid harming user privacy or introducing insecure defaults.

---

## 10. Pull Request Guidelines

All changes are merged into `main` via PRs, following these guidelines.

### 10.1 Branch naming

Use the `<type>/<short-description>` format, where `type` matches the commit types below, e.g.:

```
feat/hysteria2-parser
fix/tun-mtu-crash
docs/agent-md
refactor/node-repository
```

### 10.2 Commit messages (Conventional Commits)

Follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <subject>`.

- **Common types**: `feat` (feature), `fix` (bug fix), `refactor`, `perf` (performance), `docs`, `test`, `build` (build/deps), `ci`, `chore`.
- **scope** (optional): module or area, e.g. `parser`, `core`, `tun2socks`, `ui`, `di`, `ci`.
- **subject**: imperative mood, concise, no trailing period.
- Breaking changes are noted with `BREAKING CHANGE:` in the body.

Examples:

```
feat(parser): add Hysteria2 share link parsing
fix(core): avoid NPE when VpnService restarts on boot
build(deps): bump room to 2.7.0
```

### 10.3 PR title & description

- **Title**: also follows the Conventional Commits format, clearly summarizing the change.
- **Description** should include:
  - **What/Why**: what changed and the motivation.
  - **How**: key implementation approach (especially for protocol parsing, VPN/TUN, or native library changes).
  - **Test plan**: how it was verified (tests run, devices/scenarios manually tested).
  - **Linked issue**: `Closes #123`.
  - **Screenshots/recordings**: for UI changes.

### 10.4 Definition of Done (pre-merge checklist)

- [ ] `./gradlew assembleDebug` builds locally (full build required when touching native/Go layers).
- [ ] `./gradlew test` passes; new business logic has unit tests (see Section 4).
- [ ] CI (`android.yml`) is green.
- [ ] Dependency versions changed only in `gradle/libs.versions.toml`; no hardcoded versions.
- [ ] No secrets, keystores, `local.properties`, `app/libs/*.aar`, or other sensitive files/artifacts committed.
- [ ] Version bumps (if any) updated in `gradle.properties` (`VERSION_NAME` / `VERSION_CODE`).
- [ ] **No unsanctioned submodule changes** (`AndroidLibXrayLite/`, `hev-socks5-tunnel/`); if an update is genuinely needed, call it out in the PR description.
- [ ] Code follows the official Kotlin style and matches surrounding code.
- [ ] **`AGENT.md` updated** if the change affects anything documented here (build, tooling, versions, module layout, dependencies, CI, conventions, or distribution). See Section 11.

### 10.5 Review & merge

- Keep PRs **small and focused** — one PR solves one thing, making review easier.
- At least 1 maintainer approval is required before merging.
- Prefer **Squash and merge** to keep `main` history clean; the merge message follows Conventional Commits.
- Keep the branch in sync with `main` (rebase or merge) and resolve conflicts before merging.

---

## 11. Keeping This Document Up to Date

`AGENT.md` must stay in sync with the codebase — an outdated agent doc is worse than none, because it misleads both AI assistants and contributors. Treat it as part of the change, not an afterthought.

### When you MUST update this file (in the same PR)

| If you change… | Update section |
|----------------|----------------|
| Toolchain or versions (JDK, SDK, NDK, Go, Gradle, AGP, Kotlin, key libs) | §2 Prerequisites, §1 tech layers |
| Build steps, Gradle tasks, gomobile flow, or Windows quirks | §3 Build & Run |
| Test setup, frameworks, or notable test files | §4 Testing |
| Module layout, packages, or new/removed top-level dirs | §5 Directory Structure |
| Runtime flow (VPN/TUN/core wiring) | §6 Runtime Data Flow |
| Code style, DI, dependency, or ProGuard conventions | §7 Code Style & Conventions |
| CI workflows or release process | §8 CI & Release |
| Secrets, submodules, artifacts, or distribution channels | §9 Security & Gotchas, §1 |
| Branch/commit/PR/review process | §10 Pull Request Guidelines |

### How to keep it accurate

- **Verify against the source, not memory.** Cross-check version numbers with `gradle/libs.versions.toml`, `gradle.properties`, `AndroidLibXrayLite/go.mod`, and `gradle/wrapper/gradle-wrapper.properties`.
- **Prefer stable references over volatile values.** Where practical, point to the authoritative file (e.g. "Go version per `go.mod`") instead of duplicating a number that will drift.
- **Keep it concise and correct.** Remove or strike through content that no longer applies rather than leaving stale instructions.
- **AI assistants:** after completing a task, review whether your changes invalidated anything in this file and update it before finishing, per the §10.4 checklist.

---

## 12. Reference Docs

- `README.md` / `README_zh-CN.md` / `README_RU.md` / `README_KR.md` — user/contributor intro and build guide
- `docs/KMP_MIGRATION_PLAN.md`, `docs/FILE_MIGRATION_MAP.md`, `docs/IOS_PLATFORM_GUIDE.md`, `docs/DEPENDENCY_MIGRATION_GUIDE.md` — Kotlin Multiplatform (iOS) migration plan; consult before changing shared logic
- `app/src/main/java/com/android/xrayfa/model/README.md` — Xray config object documentation
- `AndroidLibXrayLite/README.md` — gomobile build instructions
