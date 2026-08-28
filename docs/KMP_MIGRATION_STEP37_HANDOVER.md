# KMP 迁移交接文档 — Step 37 / 阶段 E.1a（2026-08-06）

本文档记录阶段 E 第 1 项（第一批）：创建 `:core:native-bridge` KMP 模块骨架；
定义 Xray/tun2socks 底层桥接接口与双平台 factory stub，**不接入 `:app` 运行时**。

**前置**：Step 36 / D.4c 已 commit（`1dce18d`）。

---

## 前置条件检查（E.1a 入口）

| 前置项 | 状态 |
|--------|------|
| D.4a–D.4c DataStore KMP 模块完成 | ✅ committed |
| D.1 Dagger → Koin 完成 | ✅ |
| D.2 Room KMP + domain 接口分离 | ✅ |
| D.3 OkHttp → Ktor 完成 | ✅ |
| `:common` 已有高层 `XrayCore` / `TrafficDetector` 抽象 | ✅ |
| `:app` 仍通过 `XrayCoreManager` + libv2ray 运行 | ✅（本步未改） |

**结论**：阶段 D 核心项已全部完成，可进入阶段 E。可选 D 项记入下方备忘录，不阻塞 E。

---

## 改动概要

**目标**：按 `KMP_MIGRATION_PLAN.md` Phase 3.4 建立原生桥接层 KMP 模块，iOS target 可编译；
Android 行为零变化（模块未被 `:app` 依赖）。

### 新增 — `:core:native-bridge`

| 文件 | 说明 |
|------|------|
| `build.gradle.kts` | KMP 模块（android + iosArm64 + iosSimulatorArm64），依赖 `:common` |
| `XrayCoreCallback.kt` | 对齐 libv2ray `CoreCallbackHandler` 的生命周期回调 |
| `XrayCoreController.kt` | 对齐 libv2ray `CoreController` 的运行时句柄 |
| `XrayBridge.kt` | 底层 Xray gomobile/JNI 桥接接口 |
| `TunBridge.kt` | 底层 tun2socks 桥接接口 |
| `NativeBridgeFactory.kt` | `expect object` 平台工厂入口 |
| `NativeBridgeFactory.android.kt` | Android placeholder（`startLoop` 抛错，未被 app 引用） |
| `NativeBridgeFactory.ios.kt` | iOS no-op stub（编译验证用） |

**包名**：`com.android.xrayfa.nativebridge`

### 变更

| 文件 | 变更 |
|------|------|
| `settings.gradle.kts` | `include(":core:native-bridge")` |

### 未改动

- `:app` / `:tun2socks` / `XrayCoreManager` / `TProxyService` 全部保持原样
- `:common` 中 `XrayCore` 高层接口保留（E.2 再决定是否下沉或委托）
- Koin 模块无新增 binding
- libv2ray.aar / hev-socks5-tunnel JNI 仍在原模块

---

## 设计决策与原因

1. **接口 1:1 对齐 libv2ray API** —— `initCoreEnv` / `startLoop(configJson, tunFd)` / `queryStats` 等与现有 `XrayCoreManager` 调用一致，E.2 Android actual 可薄包装，不重写逻辑。
2. **与 `:common` 的 `XrayCore` 分层** —— `XrayCore` 保留 VPN 编排（parser、traffic、Toast）；`XrayBridge` 只管 native 生命周期。避免本步大范围重构 app。
3. **Android actual 用 placeholder** —— 若直接接 libv2ray 需改 Koin 与 `XrayCoreManager`；本步仅验证模块可编译，E.2 再接线。
4. **iOS stub 返回安全默认值** —— `isRunning=false`、`measureDelay=-1`，后续接 xcframework 时替换 actual 即可。
5. **不依赖 `:app`** —— Strangler Fig：先建模块、后接线、最后删旧路径。

---

## 验证状态

```bash
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :core:native-bridge:compileDebugKotlinAndroid        # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                  # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归**：VPN 连接/断开、延迟测试、流量统计（本步无代码路径变更，预期与迁移前一致）。

---

## 阶段 D 进度（完成 + 备忘录）

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.1 | Dagger → Koin | ✅ |
| D.2 | Room KMP + domain 分离 | ✅ |
| D.3 | OkHttp → Ktor | ✅ |
| D.4a–c | `:core:datastore` KMP | ✅ |
| **D.3d** | Koin 4.1+ bump | ⬜ 备忘录（可选，不阻塞 E） |
| **D.5** | Entity mapper 迁入 `:core:database` | ⬜ 备忘录（可选） |
| **D.6** | 进一步瘦身 `:common`（parser 桥接类型评估） | ⬜ 备忘录（可选） |

---

## 阶段 E 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.1a | `:core:native-bridge` 骨架 + iOS stub | ✅（本步，待 commit） |
| E.1b | Android actual 接 libv2ray / tun2socks JNI | ⬜ 下一步 |
| E.2 | `XrayCoreManager` 委托 `XrayBridge` | ⬜ |
| E.3 | `platform:vpn` — `VpnState` / `VpnController` | ⬜ |
| E.4 | iOS xcframework 构建验证 | ⬜ |
| E.5 | Compose Multiplatform / iOS 应用壳 | ⬜ |

---

## 下一步（E.1b）待办清单

1. **Android actual 接 libv2ray**
   - `:core:native-bridge:androidMain` 依赖 `libv2ray.aar`
   - 实现 `Libv2rayXrayBridge` / `Libv2rayCoreController` 委托现有 JNI
2. **Android actual 接 tun2socks（可选同批或 E.1c）**
   - 从 `:tun2socks` 提取 JNI 调用至 `HevTunBridge`
3. **Koin 注册 + `XrayCoreManager` 委托**（E.2，单独一步）
   - 保持对外 `XrayCore` 接口不变
4. **验证 gomobile iOS xcframework**（E.4，可与 E.1b 并行调研）

**验证命令（E.1b 起）**：
```bash
./gradlew :core:native-bridge:compileDebugKotlinAndroid
./gradlew :core:native-bridge:compileKotlinIosSimulatorArm64
./gradlew :app:assembleDebug
# E.2 接线后额外：
./gradlew :domain:testDebugUnitTest
```

---

## Commit 建议

```
feat(kmp): add core:native-bridge module with Xray/Tun bridge interfaces (E.1a)
```
