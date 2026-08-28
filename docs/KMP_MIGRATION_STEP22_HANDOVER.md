# KMP 迁移交接文档 — Step 22 / 阶段 D.1b（2026-08-04）

本文档记录阶段 D 第 1 项（第二批）：`ParserFactory` 改由 Koin 装配；
删除 Dagger `ParserModule`；Dagger 经 `KoinBridgeModule` 桥接，消费方零改动。

**前置**：Step 21 / D.1a 已 commit（`30ff7a1`）。

---

## 前置条件检查（D.1b 入口）

| 前置项 | 状态 |
|--------|------|
| D.1a Koin 模块 + `startKoin` | ✅ |
| `:domain` `parserDiModule()` | ✅ |
| `ParserFactory` 消费方仅 Dagger `@Inject` | ✅（4 处） |

---

## 改动概要

**目标**：parser 依赖图 **唯一来源** 为 Koin（`:domain/commonMain`）；消除 Dagger 重复装配；
Android 运行时解析行为不变。

### 新增

| 文件 | 说明 |
|------|------|
| `app/.../di/KoinBridgeModule.kt` | `@Provides ParserFactory` → `GlobalContext.get().get()` |

**原因**：ViewModel / Repository / `XrayCoreManager` 仍用 Dagger `@Inject`，
本步不批量改构造签名；桥接模块是 Strangler Fig 标准过渡手段。

### 删除

| 文件 | 说明 |
|------|------|
| `app/.../di/ParserModule.kt` | 109 行 Dagger 重复装配 |

### 调整

| 文件 | 改动 |
|------|------|
| `GlobalModule.kt` | `includes`：`ParserModule` → `KoinBridgeModule` |
| `XrayFAApplication.kt` | **`initKoin()` 先于 `onContextAvailable()`**（Dagger 建图前 Koin 就绪） |
| `ParserDiModule.kt` | 注释去掉对已删 `ParserModule` 的引用 |

**消费方未改**（仍 `@Inject ParserFactory`）：
- `XrayCoreManager`
- `XrayViewmodel` / Factory
- `SubscriptionRepository`
- `DetailViewmodel` / Factory

---

## 设计决策

1. **桥接而非改 4 个消费方** —— 逻辑零变化、diff 最小；D.1c 再逐步去掉 Dagger。
2. **Koin 先于 Dagger 初始化** —— `KoinBridgeModule.provideParserFactory()` 解析时 Koin 必须已 `startKoin`。
3. **Koin / Dagger 各持一份 Gson** —— Koin `androidDomainDiModule` 与 Dagger `provideGson()` 独立单例；
   parser 走 Koin 侧 Gson，与原先 `ParserModule` 经 Dagger Gson 行为等价（均为默认 `Gson()`）。

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
| D.1b | ParserFactory 走 Koin；删 ParserModule | ✅（待 commit） |
| D.1c | Repository / ViewModel 等 app 层迁 Koin | ⬜ |
| D.1d | 移除 Dagger | ⬜ |
| D.2 | Room Entity 与 Domain Model 分离 | ⬜ |
| D.3 | XrayConfiguration 全图 `@Serializable` | ⬜ |
| D.4 | Compose Multiplatform / iOS 壳 | ⬜ |

---

## 下一步（D.1c）待办清单

1. **扩展 Koin 模块** —— `NodeRepository`、`SubscriptionRepository`、ViewModel 依赖
2. **逐步去掉 `@Inject`** —— 从 leaf 依赖开始（或 Decompose 组件直接 `koinInject()`）
3. **缩小 `KoinBridgeModule`** —— 每迁一类依赖删一条 bridge
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
feat(kmp): route ParserFactory through Koin and remove ParserModule (D.1b)
```
