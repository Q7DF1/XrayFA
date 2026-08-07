# KMP 迁移交接文档 — Step 49 / 阶段 E.6（2026-08-07）

本文档记录阶段 E 第 6 项：`:shared` 接入 Compose Multiplatform；
`MainViewController` + iOS Koin 引导 + `AppShell` 共享 UI 骨架；**Android UI 仍用 `:androidApp`，Decompose/全页迁移留 Phase 4**。

**前置**：Step 48 / E.5e 已 commit（`e4fceb3`）。

---

## 前置条件检查（E.6 入口）

| 前置项 | 状态 |
|--------|------|
| E.5e PacketTunnel 管线 + IosVpnController | ✅ committed |
| `:shared` iOS framework 导出 | ✅ |
| Xcode iosApp 壳 | ✅ |
| Android `:androidApp` Compose UI | ✅ 未改动（Strangler Fig） |

---

## 改动概要

| 文件 | 说明 |
|------|------|
| `gradle/libs.versions.toml` | `composeMultiplatform = 1.7.3` + plugin |
| `shared/build.gradle.kts` | CMP + Material3 依赖 |
| `shared/.../ui/AppShell.kt` | 共享 Compose 壳 |
| `shared/.../ui/PlatformVpnControls.*` | iOS trial VPN 按钮；Android 空实现 |
| `shared/.../MainViewController.kt` | `ComposeUIViewController { AppShell() }` |
| `shared/.../IosKoinInit.kt` | `iosDomainDiModule` + `IosVpnController` |
| `shared/.../IosSharedInit.kt` | 改 Koin 注入 `IosVpnController` |
| `iosApp/iosApp/ContentView.swift` | SwiftUI → `MainViewControllerKt` |

### 未改动

- `:androidApp` Activity / Navigation3 / ViewModel UI
- Decompose 导航
- 共享 Home/Settings 等业务页面

---

## 设计决策与原因

1. **CMP 落在 `:shared`** —— 与 `XrayFAShared.framework` 同导出，iOS 只链一个 framework；跨平台 UI 与 domain/core 同模块边界。
2. **Android 暂不切 AppShell** —— 避免 E.6 大范围替换现有 Material3 页面；Android 行为零变化。
3. **iOS Koin 最小集** —— 仅 `iosDomainDiModule` + `IosVpnController`；DataStore/DB/Network 模块留 E.6b 按需追加。
4. **`PlatformVpnControls` expect/actual** —— VPN 试验 UI 仅 iOS 需要；common `AppShell` 保持可编译双平台。
5. **Compose 1.7.3 + Kotlin 2.1.10** —— 与当前 AGP/KMP 栈一致；全页 UI 迁移不阻塞本步。

---

## 验证状态

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug
# BUILD SUCCESSFUL

./scripts/generate_ios_xcodeproj.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -configuration Debug -arch arm64 \
  ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# BUILD SUCCEEDED
```

---

## iOS 运行状态

| 能力 | 状态 |
|------|------|
| 模拟器编译 + Compose 渲染 | ✅ |
| iOS App 显示共享 `AppShell` | ✅（需 Run 验证 UI） |
| VPN 端到端可用 | ⬜ 仍须真机 entitlement + 有效配置 |
| 与 Android 功能对等 | ⬜ UI/业务页未迁移 |

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.5a–E.5e | iOS 壳 + VPN 管线 | ✅ committed |
| E.6 | CMP AppShell + MainViewController + iOS Koin | ✅（本步，待 commit） |
| E.6b | iOS Koin 补全 DataStore/Network + 首屏迁移 | ⬜ 下一步 |
| E.5f（可选） | measureOutboundDelay shim | ⬜ |

---

## 下一步（E.6b / Phase 4 入口）待办清单

1. **iOS Koin 补全** —— `SettingsRepository`、parser、network（对齐 Android `appPlatformDiModule` 子集）
2. **迁移首个共享屏** —— 如 Home 连接按钮 + 状态（从 `:androidApp` 抽 Composable）
3. **Decompose 根组件骨架**（可选独立步）
4. **真机 NE 联调**（与 UI 并行）

---

## Commit 建议

```
feat(kmp): add Compose Multiplatform AppShell and iOS MainViewController (E.6)
```

---
