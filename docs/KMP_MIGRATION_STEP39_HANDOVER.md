# KMP 迁移交接文档 — Step 39 / 阶段 E.1c（2026-08-07）

本文档记录阶段 E 第 1 项（第三批）：`:core:native-bridge` Android actual 接 hev-socks5-tunnel JNI；
**仍不接入 `:app` 运行时**（`TProxyService` / `XrayBaseService` 接线留 E.2）。

**前置**：Step 38 / E.1b 已 commit（`e896804`）。

---

## 前置条件检查（E.1c 入口）

| 前置项 | 状态 |
|--------|------|
| E.1b XrayBridge 接 libv2ray | ✅ committed |
| `:tun2socks` `TProxyService` JNI 稳定 | ✅ |
| `:app` 仍直连 `TProxyService`（未改） | ✅ |

---

## 改动概要

**目标**：`TunBridge` Android actual 1:1 委托 `TProxyService` companion JNI，与现有 `startTun2Socks` 底层调用一致；模块可独立编译。

### 新增 — `:core:native-bridge` androidMain

| 文件 | 说明 |
|------|------|
| `HevTunBridge.kt` | 委托 `TProxyStartService` / `TProxyStopService` / `TProxyIsRunning` |

### 变更

| 文件 | 变更 |
|------|------|
| `NativeBridgeFactory.android.kt` | `createTunBridge()` → `HevTunBridge()` |
| `build.gradle.kts` | androidMain `compileOnly(project(":tun2socks"))` |

### 未改动

- `:app` / `XrayBaseService` / `TProxyService.startTun2Socks`（仍走 `util.configure(context)` 生成 config）
- `:tun2socks` 源码
- iOS TunBridge stub
- Koin / 运行时接线

---

## 设计决策与原因

1. **复用 `TProxyService` companion JNI** —— 不重复声明 `external fun`，JNI 签名单一来源；`ensureNativeLoaded()` 触发 companion `loadLibrary`。
2. **`isRunning()` 用 `TProxyIsRunning()`** —— 读 native 状态，而非 `TProxyService.running` 实例字段（与底层语义一致）。
3. **`compileOnly(:tun2socks)`** —— 同 libv2ray 策略：library AAR 不打包 JNI .so；`:app` 已依赖 `:tun2socks` 提供运行时。
4. **`TunBridge.startTun2Socks(configPath, tunFd)`** —— 低层 API 接收已生成 config 路径；高层 `TProxyService` 仍负责 `Tun2SocksConfigUtil.configure()`，E.2 接线时再统一 orchestration。

---

## 验证状态

```bash
./gradlew :core:native-bridge:bundleDebugAar                           # BUILD SUCCESSFUL
./gradlew :core:native-bridge:compileDebugKotlinAndroid                # BUILD SUCCESSFUL
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64           # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                          # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归（本步无运行时变更，E.2 后必测）**：VPN TUN 连接、断开、重连。

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1a | 模块骨架 + iOS stub | ✅ committed |
| E.1b | Android XrayBridge 接 libv2ray | ✅ committed |
| E.1c | Android TunBridge 接 hev-socks5-tunnel | ✅（本步，待 commit） |
| E.2 | `XrayCoreManager` / VPN 服务委托 native-bridge | ⬜ 下一步 |
| E.3 | `platform:vpn` — `VpnState` / `VpnController` | ⬜ |

---

## 下一步（E.2）待办清单

1. **Koin 注册**
   - `single<XrayBridge> { NativeBridgeFactory.createXrayBridge() }`
   - `single<TunBridge> { NativeBridgeFactory.createTunBridge() }`
   - `:app` 添加 `implementation(project(":core:native-bridge"))`
2. **`XrayCoreManager` 委托 `XrayBridge`**
   - `Libv2ray.initCoreEnv` → `xrayBridge.initCoreEnv`
   - `Libv2ray.newCoreController` → `xrayBridge.newCoreController`
   - `measureOutboundDelay` / controller 操作同理
   - 保留 parser、traffic、Toast、Settings 逻辑不变
3. **（可选同批）`TProxyService` 或 `XrayBaseService` 委托 `TunBridge`**
   - `startTun2Socks(fd)` 仍用 `util.configure(context)` 得 path，再调 `tunBridge.startTun2Socks(path, fd)`
4. **删除 `:app` 内 direct `libv2ray` import**（若全部经 bridge）

**验证命令（E.2）**：
```bash
./gradlew :app:assembleDebug
./gradlew :domain:testDebugUnitTest
```

**手动回归**：VPN 连接/断开、延迟测试、流量统计、TUN 重连。

---

## Commit 建议

```
feat(kmp): wire Android TunBridge to hev-socks5-tunnel JNI in core:native-bridge (E.1c)
```
