# KMP 迁移交接文档 — Step 40 / 阶段 E.2（2026-08-07）

本文档记录阶段 E 第 2 项：Koin 注册 `XrayBridge` / `TunBridge`；
`XrayCoreManager` 与 VPN Tun 路径委托 `:core:native-bridge`，**对外行为不变**。

**前置**：Step 39 / E.1c 已 commit（`fb7e63b`）。

---

## 前置条件检查（E.2 入口）

| 前置项 | 状态 |
|--------|------|
| E.1b XrayBridge Android actual | ✅ |
| E.1c TunBridge Android actual | ✅ committed |
| `:app` 仍提供 libv2ray.aar + `:tun2socks` JNI | ✅ |

---

## 改动概要

**目标**：Android 运行时经 native-bridge 访问 JNI；`:app` 移除 direct `libv2ray` import。

### 新增 — `:app`

| 文件 | 说明 |
|------|------|
| `BridgedTun2SocksService.kt` | `Tun2SocksService` 实现，委托 `TunBridge`；镜像原 `TProxyService` orchestration |

### 变更

| 文件 | 变更 |
|------|------|
| `XrayCoreManager.kt` | 注入 `XrayBridge`；`CoreController` → `XrayCoreController`；移除 `libv2ray.*` import |
| `AppCoreDiModule.kt` | 注册 `XrayBridge` / `TunBridge` singleton |
| `AppComponentDiModule.kt` | `TProxyService` → `BridgedTun2SocksService` |
| `app/build.gradle.kts` | `implementation(project(":core:native-bridge"))` |

### 未改动

- `:common` `XrayCore` 接口
- `XrayBaseService` VPN 流程（hexTun / TUN fd 分支）
- `:tun2socks` `TProxyService` 源码（保留，暂未删除）
- parser、traffic、Toast、Settings 逻辑

---

## 设计决策与原因

1. **`BridgedTun2SocksService` 放 `:app`** —— 避免 `:tun2socks` ↔ `:core:native-bridge` 环依赖（native-bridge 已 `compileOnly(:tun2socks)`）。
2. **保留 `running` 实例字段** —— 与旧 `TProxyService.isRunning()` 语义一致（非 native `TProxyIsRunning()`）。
3. **Log tag 仍为 `TProxyService`** —— 便于 logcat 过滤连续性。
4. **libv2ray 仍由 `:app` `fileTree(libs/*.aar)` 提供** —— native-bridge 用 `compileOnly`，运行时 classpath 不变。

---

## 验证状态

```bash
./gradlew :core:native-bridge:bundleDebugAar    # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                    # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest              # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归（本步有运行时路径变更，必测）**：
- VPN 连接 / 断开 / 重连
- hexTun 模式与普通 TUN 模式
- 节点延迟测试（运行中 + outbound）
- 流量统计显示
- 设置页 Xray 版本号写入

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1a–c | native-bridge 骨架 + Android actual | ✅ committed |
| E.2 | app 委托 native-bridge | ✅（本步，待 commit） |
| E.3 | `platform:vpn` — `VpnState` / `VpnController` | ⬜ 下一步 |
| E.4 | iOS xcframework 构建验证 | ⬜ |
| E.5 | Compose Multiplatform / iOS 应用壳 | ⬜ |

---

## 下一步（E.3）待办清单

1. **创建 `platform:vpn` KMP 模块**
   - `VpnState` sealed interface
   - `VpnController` 接口（connect / disconnect / permission）
2. **Android `VpnController` actual** —— 包装现有 `XrayBaseService` / `XrayBaseServiceManager`
3. **iOS stub** —— compile-only，待 Network Extension
4. **（可选）删除未使用的 `TProxyService` Koin 路径** —— 确认无其他引用后清理

**验证命令**：
```bash
./gradlew :platform:vpn:compileKotlinIosSimulatorArm64
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): wire XrayCoreManager and Tun service through core:native-bridge (E.2)
```
