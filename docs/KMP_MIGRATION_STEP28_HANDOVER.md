# KMP 迁移交接文档 — Step 28 / 阶段 D.2a（2026-08-05）

本文档记录阶段 D 第 2 项（第一批）：引入 **Domain Model** 与 **Room Entity** 分离层；
Repository 对外返回 domain 类型；删除 deprecated `Link` 死代码。

**前置**：Step 27 / D.1d-c 已 commit（`f60376b`）。

---

## 改动概要

**目标**：持久化类型与业务模型解耦，`:domain` 持有可跨平台共享的 `Node`/`Subscription`；
`:app` 保留 Room Entity 与 Mapper，为后续 `:core:database` 提取铺路。

### 新增 — `:domain`

| 文件 | 说明 |
|------|------|
| `model/Node.kt` | 平台无关节点 domain model |
| `model/Subscription.kt` | 平台无关订阅 domain model |

### 新增 — `:app`

| 文件 | 说明 |
|------|------|
| `dto/NodeEntity.kt` | `@Entity(tableName = "Node")` |
| `dto/SubscriptionEntity.kt` | `@Entity(tableName = "Subscription")` |
| `dto/EntityMappers.kt` | Entity ↔ Domain 双向映射 |

### 删除

| 文件 | 原因 |
|------|------|
| `dto/Node.kt` / `dto/Subscription.kt` | 由 Entity + Domain 替代 |
| `dto/Link.kt` | 已废弃，解析链改用 `ParseLinkInput` |
| `dto/ParseAdapters.kt` | 合并入 `EntityMappers.kt` |
| `dao/LinkDao.kt` | 未接入 Database，死代码 |

### 重构

| 层 | 变更 |
|----|------|
| `ParseModels.kt` | `ParsedNode` → `typealias ParsedNode = model.Node` |
| `NodeDao` / `SubscriptionDao` | 操作 `*Entity` |
| `NodeRepository` / `SubscriptionRepository` | 对外 `Flow<List<Node>>` 等 domain 类型；内部 map |
| ViewModel / UI（5 文件） | import 改为 `com.android.xrayfa.model.*` |
| `SubscriptionRepository.fetchAndSaveNodes` | 直接构造 `ParseLinkInput`，去掉 `Link` |
| `XrayViewmodel.addLink` | 同上 |

**数据库 schema / migration 未改动**（version 4，表名 `Node`/`Subscription` 不变）。

---

## 设计决策与原因

1. **`ParsedNode` 保留为 typealias** —— Parser 层零逻辑改动，输出即 domain `Node`。
2. **Entity 显式 `tableName`** —— 类重命名为 `*Entity` 不影响已有 SQLite 表名与 migration。
3. **Repository 边界映射** —— UI/ViewModel 不再依赖 Room 注解类型，跨平台共享路径清晰。
4. **本步不建 `:core:database`** —— 先验证 mapper 层与行为一致性，模块提取放 D.2b。

---

## 验证状态

```bash
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest   # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64                        # BUILD SUCCESSFUL
./gradlew :app:compileDebugKotlin                                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                            # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.1d-c | 移除 Dagger | ✅ committed |
| D.2a | Domain Model + Entity 分离 + 去 Link | ✅（待 commit） |
| D.2b | Repository 接口提取至 `:domain` | ⬜ |
| D.2c | 创建 `:core:database` Room KMP 模块 | ⬜ |

---

## 下一步（D.2b）待办清单

1. **在 `:domain` 定义 `NodeRepository` / `SubscriptionRepository` 接口** —— 返回 domain 类型
2. **`:app` Repository 实现接口** —— Koin 绑定接口 → 实现
3. **ViewModel 依赖接口而非具体类** —— 便于 iOS 复用 domain 层测试
4. **（可选）Room schema export** —— 为 `:core:database` 迁移准备 golden schema

**验证命令**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileDebugKotlinAndroid :domain:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): separate Room entities from domain Node and Subscription (D.2a)
```
