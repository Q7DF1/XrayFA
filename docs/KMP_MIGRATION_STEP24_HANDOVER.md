# KMP 迁移交接文档 — Step 24 / 阶段 D.1c+（2026-08-04）

本文档记录阶段 D 第 1 项（第四批）：`SettingsRepository`、`SubscriptionParser`、
`OkHttpClient`（`@ShortTime` / `@LongTime`）改由 Koin 唯一装配；删除 Dagger
`NetworkModule` 及 `GlobalModule` 中重复的 DB/DAO/Repository 提供项。

**前置**：Step 23 / D.1c 已 commit（`0aa00e0`）。

---

## 改动概要

**目标**：数据层 + 网络层 + settings 在 Koin 侧 **单点装配**；Dagger 仅保留桥接；
消除双份 `OkHttpClient` / `SubscriptionParser` / `SettingsRepository` 工厂。

### `KoinBridgeModule` 扩展

| 桥接类型 | Koin 来源 |
|----------|-----------|
| `SettingsRepository` | `appPlatformDiModule` |
| `SubscriptionParser` | `appDataDiModule` |
| `OkHttpClient` `@ShortTime` | `appNetworkDiModule` |
| `OkHttpClient` `@LongTime` | `appNetworkDiModule` |

**原因**：`XrayViewmodel`、`SettingsViewmodel`、`XrayCoreManager` 等仍 `@Inject` 上述类型；
桥接后它们自动拿到 Koin 单例，无需改 Factory 代码。

### 删除 / 精简 Dagger

| 变更 | 说明 |
|------|------|
| 删除 `NetworkModule.kt` | 逻辑已在 `AppNetworkDiModule` |
| `GlobalModule` 移除 | `provideXrayDatabase`、`provideNodeDao`、`provideSubscriptionDao`、`provideBase64Parser`、`provideSettingsRepository` |
| `GlobalModule.includes` | 去掉 `NetworkModule` |

**消费方仍零改动** — ViewModel / Service / Receiver 继续 `@Inject`。

---

## 设计决策

1. **SettingsRepository 统一走 Koin** —— DataStore + Logger 由 `appPlatformDiModule` 构造；
   Dagger 不再独立 `new SettingsRepository()`，避免双实例。
2. **OkHttp 单例** —— `@ShortTime` / `@LongTime` 各一份，与旧 `NetworkModule` 行为一致。
3. **DB/DAO 仅 Koin 装配** —— `XrayFADatabase.INSTANCE` 缓存保证与旧路径同一 Room 实例。

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
| D.1c | Repository + data/network Koin | ✅ committed |
| D.1c+ | Settings/OkHttp/Parser 桥接；删 Dagger 重复项 | ✅（待 commit） |
| D.1d | ViewModel Factory / Core 迁 Koin；移除 Dagger | ⬜ |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ |

---

## 下一步（D.1d 首批）待办清单

1. **桥接 platform 类型** —— `Logger`、`GeoIpProvider`、`XrayAssetPaths`（或去 `@Inject`）
2. **桥接 `XrayCoreManager` / ViewModel Factory** —— 或改 `koinViewModel`
3. **删除 `GlobalModule` 剩余 `@Provides` / `@Binds`** —— 最终移除 `XrayFAComponent`、KSP

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
feat(kmp): bridge settings and OkHttp from Koin; drop Dagger NetworkModule (D.1c+)
```
