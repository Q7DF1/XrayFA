# KMP 迁移交接文档 — Step 38 / 阶段 E.1b（2026-08-06）

本文档记录阶段 E 第 1 项（第二批）：`:core:native-bridge` Android actual 接 libv2ray JNI；
**仍不接入 `:app` 运行时**（`XrayCoreManager` 接线留 E.2）。

**前置**：Step 37 / E.1a 已 commit（`bded7a1`）。

---

## 前置条件检查（E.1b 入口）

| 前置项 | 状态 |
|--------|------|
| E.1a 接口 + factory 骨架 | ✅ committed |
| `app/libs/libv2ray.aar` 存在 | ✅ |
| `:app` 仍直连 libv2ray（未改） | ✅ |

---

## 改动概要

**目标**：Android actual 1:1 委托 gomobile libv2ray，与 `XrayCoreManager` 现有调用语义一致；模块可独立编译验证 JNI 绑定。

### 新增 — `:core:native-bridge` androidMain

| 文件 | 说明 |
|------|------|
| `Libv2rayCoreCallbackAdapter.kt` | `XrayCoreCallback` → `CoreCallbackHandler` |
| `Libv2rayCoreControllerAdapter.kt` | `CoreController` → `XrayCoreController` |
| `Libv2rayXrayBridge.kt` | `XrayBridge` 实现，委托 `Libv2ray.*` |

### 变更

| 文件 | 变更 |
|------|------|
| `NativeBridgeFactory.android.kt` | `createXrayBridge()` → `Libv2rayXrayBridge()`；Tun 仍 placeholder |
| `build.gradle.kts` | androidMain 用 `compileOnly` 依赖 `app/libs/libv2ray.aar`（AGP 禁止 AAR 模块 `implementation` 本地 .aar） |

### 未改动

- `:app` / `XrayCoreManager` / Koin 绑定
- `:tun2socks` / `TProxyService`（TunBridge 留 E.1c）
- iOS stub 不变

---

## 设计决策与原因

1. **Adapter 模式** —— 不修改 libv2ray 生成类，仅包装为 KMP 接口，E.2 替换 `XrayCoreManager` 内部实现时行为不变。
2. **aar 路径复用 `app/libs` + compileOnly** —— 与 `:app` 共用 libv2ray 二进制；library 模块不能 `implementation` 本地 .aar（`bundleDebugAar` 会失败），E.2 接线后由 `:app` 提供运行时 JNI。
3. **TunBridge 分步（E.1c）** —— hev-socks5-tunnel JNI 在 `:tun2socks` 模块内，需评估依赖方向后再接，本步 scope 控制在 Xray。
4. **仍不依赖 `:app`** —— 编译验证 bridge 层，运行时零影响。

---

## 验证状态

```bash
./gradlew :core:native-bridge:bundleDebugAar                           # BUILD SUCCESSFUL
./gradlew :core:native-bridge:compileDebugKotlinAndroid        # BUILD SUCCESSFUL
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                  # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1a | 模块骨架 + iOS stub | ✅ committed |
| E.1b | Android XrayBridge 接 libv2ray | ✅（本步，待 commit） |
| E.1c | Android TunBridge 接 hev-socks5-tunnel | ⬜ 下一步 |
| E.2 | `XrayCoreManager` 委托 `XrayBridge` | ⬜ |
| E.3 | `platform:vpn` — `VpnState` / `VpnController` | ⬜ |

---

## 下一步（E.1c / E.2）待办清单

1. **E.1c — TunBridge Android actual**
   - 依赖 `:tun2socks` 或提取 JNI extern 至 native-bridge
   - 实现 `HevTunBridge` 委托 `TProxyStartService` / `TProxyStopService`
2. **E.2 — 接线 `XrayCoreManager`**
   - Koin 注册 `XrayBridge`（`NativeBridgeFactory.createXrayBridge()`）
   - `XrayCoreManager` 内部 `Libv2ray.newCoreController` → `xrayBridge.newCoreController`
   - 保持对外 `XrayCore` 接口与 Toast/traffic/parser 逻辑不变
3. **手动回归（E.2 后）**：VPN 连接、延迟测试、流量统计、版本号写入设置

**验证命令（E.2 起）**：
```bash
./gradlew :core:native-bridge:compileDebugKotlinAndroid
./gradlew :app:assembleDebug
./gradlew :domain:testDebugUnitTest
```

---

## Commit 建议

```
feat(kmp): wire Android XrayBridge to libv2ray JNI in core:native-bridge (E.1b)
```
