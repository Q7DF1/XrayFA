# KMP 迁移交接文档 — Step 41 / 阶段 E.3（2026-08-07）

本文档记录阶段 E 第 3 项：创建 `:platform:vpn` KMP 模块；
Koin 注册 `AppVpnController`，**ViewModel 仍用 `XrayBaseServiceManager`（零 UI 变更）**。

**前置**：Step 40 / E.2 已 commit（`5405f2e`）。

---

## 前置条件检查（E.3 入口）

| 前置项 | 状态 |
|--------|------|
| E.2 native-bridge 接线完成 | ✅ committed |
| `XrayBaseService.statusFlow` 稳定 | ✅ |

---

## 改动概要

**目标**：建立跨平台 VPN 控制契约；Android 实现委托现有 Service Manager；iOS 可编译 stub。

### 新增 — `:platform:vpn`

| 文件 | 说明 |
|------|------|
| `build.gradle.kts` | KMP 模块（android + iosArm64 + iosSimulatorArm64） |
| `VpnState.kt` | `Disconnected` / `Connected`（对齐当前 boolean status） |
| `VpnController.kt` | `state` + `connect` / `disconnect` / `restartIfNeeded` |
| `IosVpnController.kt` | iOS no-op stub |
| `AndroidVpnControllerStub.kt` | androidMain 占位（未被 app 使用） |

### 新增 — `:app`

| 文件 | 说明 |
|------|------|
| `AppVpnController.kt` | 实现 `VpnController`，委托 `XrayBaseServiceManager` + `statusFlow` 映射 |

### 变更

| 文件 | 变更 |
|------|------|
| `settings.gradle.kts` | `include(":platform:vpn")` |
| `app/build.gradle.kts` | `implementation(project(":platform:vpn"))` |
| `AppCoreDiModule.kt` | `single<VpnController> { AppVpnController(...) }` |

### 未改动

- `XrayViewmodel` / `SettingsViewmodel` 仍注入 `XrayBaseServiceManager`
- `XrayBaseService` / VPN Service 逻辑
- UI 层 `VpnService.prepare()` 权限流程

---

## 设计决策与原因

1. **`VpnState` 先用 Disconnected/Connected** —— 与现有 `statusFlow: Boolean` 1:1 映射；Connecting/Error 等留后续 iOS/Android 细化。
2. **`AppVpnController` 放 `:app`** —— 需引用 `XrayBaseService` / `XrayBaseServiceManager`，避免 platform 模块反向依赖 app。
3. **Koin 注册但不改 ViewModel** —— Strangler Fig：接口就绪，E.3b 再逐步替换 ViewModel 注入。
4. **androidMain stub** —— 满足 KMP android target 编译；运行时由 `AppVpnController` 提供。

---

## 验证状态

```bash
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :platform:vpn:bundleDebugAar                   # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                            # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                      # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归**：VPN 连接/断开（本步 ViewModel 路径未变，预期与 E.2 一致）。

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1a–c | native-bridge | ✅ committed |
| E.2 | app 委托 native-bridge | ✅ committed |
| E.3 | platform:vpn 模块 + AppVpnController | ✅（本步，待 commit） |
| E.3b | ViewModel 迁移至 `VpnController` | ⬜ 下一步 |
| E.4 | iOS xcframework 构建验证 | ⬜ |
| E.5 | Compose Multiplatform / iOS 壳 | ⬜ |

---

## 下一步（E.3b / E.4）待办清单

1. **E.3b — ViewModel 逐步改用 `VpnController`**
   - `XrayViewmodel`：`XrayBaseServiceManager` → `VpnController`（或两者并存过渡）
   - `SettingsViewmodel.restartIfNeeded` 走 `vpnController.restartIfNeeded()`
   - 删除对 `XrayBaseService.statusFlow` 的直接 collect（改 collect `vpnController.state`）
2. **E.4 — gomobile iOS xcframework 验证**
   - `gomobile bind -target ios,iossimulator` on AndroidLibXrayLite
3. **（可选）扩展 `VpnState`** —— Connecting / Disconnecting / Error

**验证命令（E.3b 起）**：
```bash
./gradlew :app:assembleDebug
# 手动：首页连接、设置页重启 VPN、Quick Settings Tile
```

---

## Commit 建议

```
feat(kmp): add platform:vpn module with AppVpnController bridge (E.3)
```
