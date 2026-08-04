# KMP 迁移交接文档 — Step 23 / 阶段 D.1c（2026-08-04）

本文档记录阶段 D 第 1 项（第三批）：`NodeRepository` / `SubscriptionRepository`
及数据层、网络层依赖迁入 Koin；Dagger 经 `KoinBridgeModule` 桥接；消费方零改动。

**前置**：Step 22 / D.1b 已 commit（`2ed6099`）。

---

## 前置条件检查（D.1c 入口）

| 前置项 | 状态 |
|--------|------|
| D.1b `ParserFactory` 走 Koin | ✅ |
| `KoinBridgeModule` 桥接模式 | ✅ |
| Repository 仅 Dagger `@Inject` 消费 | ✅ |

---

## 改动概要

**目标**：Repository 层装配迁入 Koin，为后续 Room KMP / 共享 Repository 打基础；
订阅拉取、节点 CRUD 逻辑不变。

### 新增 Koin 模块

| 文件 | 说明 |
|------|------|
| `di/AppDataDiModule.kt` | DB、DAO、`SubscriptionParser`、两个 Repository |
| `di/AppNetworkDiModule.kt` | `Interceptor`、`ShortTime` / `LongTime` OkHttpClient |
| `di/KoinQualifiers` | `ShortTime` / `LongTime` 常量（对齐 Dagger qualifier 名） |

**原因**：
- `SubscriptionRepository` 依赖 DB + 网络 + parser，需一并迁入 Koin 图，避免 Dagger/Koin 循环依赖。
- `LongTime` OkHttp 本步一并定义（`SettingsViewmodel` 仍走 Dagger `NetworkModule`，行为不变）；
  Koin 侧预置供 D.1c 后续批次桥接。

### Repository 去 `javax.inject`

| 文件 | 改动 |
|------|------|
| `NodeRepository.kt` | 移除 `@Inject` / `@Singleton` |
| `SubscriptionRepository.kt` | 移除 `@Inject` / `@Singleton` / `@ShortTime` |

### 桥接扩展

| 文件 | 改动 |
|------|------|
| `KoinBridgeModule.kt` | 新增 `provideNodeRepository` / `provideSubscriptionRepository` |
| `KoinModules.kt` | `androidKoinModules()` 加入 `appNetworkDiModule`、`appDataDiModule` |

**消费方未改**（仍 `@Inject`）：
- `XrayViewmodel` / Factory
- `SubscriptionViewmodel` / Factory
- `DetailViewmodel` / Factory
- `XrayBaseServiceManager`

---

## 设计决策

1. **DB 单例安全** —— `XrayFADatabase.getXrayDatabase()` 内部 `INSTANCE` 缓存；
   Koin 与 Dagger 并行期间仍共享同一 Room 实例。
2. **Network 逻辑复制而非改 Dagger** —— `AppNetworkDiModule` 与 `NetworkModule` 代码等价；
   Dagger `NetworkModule` 暂留（ViewModel 仍用 `@ShortTime` / `@LongTime`）。
3. **本步只迁 Repository** —— ViewModel Factory、`XrayCoreManager` 等留 D.1c 后续批次。

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
| D.1a | Koin 基础设施 + domain parser module | ✅ committed |
| D.1b | ParserFactory 走 Koin | ✅ committed |
| D.1c | Repository 迁 Koin | ✅（待 commit） |
| D.1c+ | ViewModel / Core 迁 Koin；删 Dagger Network/Global 重复项 | ⬜ |
| D.1d | 移除 Dagger | ⬜ |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ |

---

## 下一步（D.1c+）待办清单

1. **桥接或迁移 ViewModel 依赖** —— `SubscriptionParser`、`OkHttpClient`（`@ShortTime`/`@LongTime`）
2. **桥接 `SettingsRepository`** —— 统一 Koin 单例（Dagger 仍 `@Provides` 时可删一条）
3. **删除 Dagger 重复 `@Provides`** —— `provideNodeDao`、`provideSubscriptionDao`、`provideBase64Parser`（确认无 Dagger 直注入后）
4. **可选**：`checkModules { androidKoinModules() }` 单元测试

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
feat(kmp): migrate repositories and data/network wiring to Koin (D.1c)
```
