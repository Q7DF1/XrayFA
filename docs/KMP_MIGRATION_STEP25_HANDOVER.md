# KMP 迁移交接文档 — Step 25 / 阶段 D.1d-a（2026-08-05）

本文档记录阶段 D 第 1 项（第五批）：platform 类型、`XrayCoreManager`、
协程 Scope、`AppInfoRepository` 迁入 Koin；删除 Dagger `CoroutinesModule` 及
`GlobalModule` 中对应 `@Binds` / 重复 `@Provides`。

**前置**：Step 24 / D.1c+ 已 commit（`30e5998`）。

---

## 改动概要

**目标**：Core / platform 层由 Koin 唯一装配；Dagger 仅保留 Service 绑定与桥接。

### 新增 Koin 模块

| 文件 | 说明 |
|------|------|
| `AppCoroutineDiModule.kt` | `@Main` / `@Background` `CoroutineScope` |
| `AppCoreDiModule.kt` | `XrayCoreManager`、`XrayCore`、`AppInfoRepository` |

### `KoinBridgeModule` 扩展

| 桥接类型 | 说明 |
|----------|------|
| `Logger` / `GeoIpProvider` / `XrayAssetPaths` | platform |
| `@Main` / `@Background` `CoroutineScope` | 协程 |
| `AppInfoRepository` | 应用列表 |
| `XrayCoreManager` / `XrayCore` / `TrafficDetector` | VPN 核心 |

### 去 `javax.inject`

| 类 | 说明 |
|----|------|
| `AndroidLogger` | Koin `single<Logger>` |
| `AndroidGeoIpProvider` | Koin `single<GeoIpProvider>` |
| `AndroidXrayAssetPaths` | Koin `single<XrayAssetPaths>` |
| `XrayCoreManager` | Koin `appCoreDiModule` |
| `AppInfoRepository` | Koin `appCoreDiModule` |

### 删除 / 精简 Dagger

| 变更 | 说明 |
|------|------|
| 删除 `CoroutinesModule.kt` | 已在 `AppCoroutineDiModule` |
| `GlobalModule` 移除 | platform `@Binds`、`provideGson`、`provideSettingsDataStore` |
| **保留** | `bindTun2SocksService`（tun2socks 仍 Dagger 注入） |

**消费方仍零改动** — ViewModel Factory / Service / Receiver 继续 `@Inject`。

---

## 设计决策

1. **`XrayCore` / `TrafficDetector` 均指向同一 `XrayCoreManager` 实例** —— 与旧 `@Binds` 语义一致。
2. **协程 Scope 统一走 Koin** —— `BootBroadcastReceiver`、`NotificationHelper` 等 `@Background` 注入拿到 Koin 单例。
3. **`GlobalModule` 仅留 Android 壳依赖** —— Context、Executor、NetPreferences、Tun2Socks；下一步 D.1d-b 迁 ViewModel Factory。

---

## 验证状态

```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest   # BUILD SUCCESSFUL
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest     # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64                        # BUILD SUCCESSFUL
./gradlew :app:compileDebugKotlin                                       # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.1c+ | Settings/OkHttp 桥接 | ✅ committed |
| D.1d-a | Platform + Core + Coroutines 迁 Koin | ✅（待 commit） |
| D.1d-b | ViewModel Factory / NotificationHelper 迁 Koin | ⬜ |
| D.1d-c | 移除 Dagger（Component、KSP） | ⬜ |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ |

---

## 下一步（D.1d-b）待办清单

1. **Koin 注册 ViewModel Factory**（5 个）或引入 `koin-androidx-viewmodel`
2. **桥接 `NotificationHelper`、`XrayBaseServiceManager`**
3. **删除 `GlobalModule` 剩余 `@Provides`**（Context/Executor 可留至最后）

**验证命令**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
```

---

## Commit 建议

```
feat(kmp): migrate platform and core services to Koin (D.1d-a)
```
