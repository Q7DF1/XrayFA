# KMP 迁移交接文档 — Step 30 / 阶段 D.2c（2026-08-05）

本文档记录阶段 D 第 2 项（第三批）：创建 `:core:database` KMP 模块，
迁移 Room Entity / DAO / Database / Migration；`:app` 移除本地 Room 代码。

**前置**：Step 29 / D.2b 已 commit（`87a1af4`）。

---

## 改动概要

**目标**：数据库层独立为 KMP 模块，Android 行为与 schema/migration 不变；为 iOS Room 铺路。

### 新增模块 `:core:database`

| 路径 | 说明 |
|------|------|
| `entity/NodeEntity.kt` / `SubscriptionEntity.kt` | Room Entity（`tableName` 不变） |
| `dao/NodeDao.kt` / `SubscriptionDao.kt` | DAO 接口 |
| `XrayFADatabase.kt` | `@Database(version = 4, exportSchema = true)` |
| `migration/XrayFADatabaseMigrations.kt` | 保留 1→2→3→4 全部 migration |
| `AndroidXrayDatabaseFactory.kt` | Android 单例 + DAO 工厂 |
| `schemas/` | Room schema export（KSP 生成） |

### `:app` 删除

- `dao/*`（XrayFADatabase、NodeDao、SubscriptionDao）
- `dto/NodeEntity.kt`、`dto/SubscriptionEntity.kt`
- Room KSP / `room-ktx` 依赖

### `:app` 保留

- `dto/EntityMappers.kt` — Entity ↔ Domain 映射（依赖 `:core:database` entity）

### 构建

| 变更 | 说明 |
|------|------|
| `settings.gradle.kts` | `include(":core:database")` |
| `libs.versions.toml` | `room-runtime`、`sqlite-bundled` |
| `app/build.gradle.kts` | `implementation(project(":core:database"))`，移除 KSP |

---

## 设计决策与原因

1. **iOS target 暂缓** —— Room 2.7 iOS klib 需 Kotlin 2.1.10+，当前项目 2.0.21；待 Phase 0 Kotlin 升级后再启用 `iosArm64` + KSP + BundledSQLite。
2. **app 不直接依赖 Room 类型** —— Koin 通过 `AndroidXrayDatabaseFactory.getNodeDao()` 注入 DAO，app 无需 `room-ktx` classpath。
3. **`exportSchema = true`** —— golden schema 写入 `core/database/schemas/`，供后续 migration 测试。
4. **Migration SQL 原样迁移** —— Android 升级路径零变化。

---

## 验证状态

```bash
./gradlew :core:database:compileDebugKotlinAndroid                        # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64                         # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                                              # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.2a | Domain Model + Entity 分离 | ✅ committed |
| D.2b | Repository 接口提取 | ✅ committed |
| D.2c | `:core:database` 模块 | ✅（待 commit） |
| D.3 | 网络层 Ktor / 其余 Phase 2 | ⬜ 下一步 |

---

## 下一步（D.3 / Phase 2 续）待办清单

1. **Kotlin 升级至 2.1.x** —— 解锁 Room iOS KSP + 其他 KMP 依赖
2. **`:core:database` 启用 iOS target** —— BundledSQLite + `IosXrayDatabaseFactory`
3. **OkHttp → Ktor**（`:core:network`）或按 `KMP_MIGRATION_PLAN.md` Phase 2 顺序
4. **（可选）Entity mapper 迁入 `:core:database` 或 `:domain`**

**验证命令**：
```bash
./gradlew :core:database:compileKotlinIosSimulatorArm64   # Kotlin 升级后
./gradlew :common:testDebugUnitTest
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): extract Room layer into core:database module (D.2c)
```
