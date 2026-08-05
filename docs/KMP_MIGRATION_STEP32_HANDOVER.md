# KMP 迁移交接文档 — Step 32 / 阶段 D.3b（2026-08-05）

本文档记录阶段 D 第 3 项（第二批）：Kotlin 2.1.10 升级、`:core:database` 启用 iOS target + Room KSP + BundledSQLite。

**前置**：Step 31 / D.3a 已 commit（`2b0338d`）。

---

## 前置条件检查（D.3b 入口）

| 前置项 | 状态 |
|--------|------|
| D.3a `:core:network` + 订阅 Ktor | ✅ committed |
| D.2c `:core:database` Android Room | ✅ committed |
| `:domain` / `:core:network` iOS 编译 | ✅（D.3a 已验证） |
| Kotlin 2.1.10+（Room iOS klib ABI 要求） | ✅ 本步完成 |

---

## 改动概要

**目标**：解锁 Room iOS KSP 编译路径；Android 数据库行为（schema / migration）不变。

### 1. 工具链升级

| 文件 | 变更 |
|------|------|
| `gradle/libs.versions.toml` | `kotlin` 2.0.21 → **2.1.10**；`ksp` → **2.1.10-1.0.31** |
| `gradle.properties` | 新增 `kotlin.native.toolchain.enabled=false`（KT-74278 workaround） |

**原因**：Room 2.7 iOS klib 需 Kotlin 2.1.10+ ABI；KSP 版本须与 Kotlin 严格对齐。

### 2. `:core:database` iOS 启用

| 文件 | 说明 |
|------|------|
| `build.gradle.kts` | 启用 `iosArm64` / `iosSimulatorArm64`；`kspIos*` + `sqlite-bundled` |
| `XrayFADatabase.kt` | 添加 `@ConstructedBy`（Room KMP 非 Android 平台必需） |
| `XrayFADatabaseConstructor.kt` | `expect object`；Room KSP 自动生成 `actual` |
| `IosXrayDatabaseFactory.kt` | iOS 单例 + `BundledSQLiteDriver` + Documents 路径 |
| `migration/XrayFADatabaseMigrations.kt` | **commonMain → androidMain**（iOS 新安装无需 migration） |

**未改动**：
- `AndroidXrayDatabaseFactory` — 仍用 `Room.databaseBuilder` + 1→2→3→4 migration，Android 升级路径不变
- `:app` 运行时逻辑 — 仍通过 Koin 注入 `AndroidXrayDatabaseFactory` 的 DAO

---

## 设计决策与原因

1. **Migration 仅留 androidMain** —— `SupportSQLiteDatabase` 是 Android API；iOS 新安装直接创建 v4 schema，符合 `KMP_MIGRATION_PLAN.md` Phase 2 策略。
2. **`@ConstructedBy` + expect constructor** —— Room KMP 在 commonMain 面向 iOS 编译时的强制要求；KSP 为各平台生成 actual。
3. **Koin 暂留 4.0.1** —— 本步聚焦工具链 + Room iOS；Koin 4.1+ bump 可随后续步骤单独做。
4. **`kotlin.native.toolchain.enabled=false`** —— 规避 KT-74278（KSP iOS 任务在 K/N 分发未完成时 `ClassNotFoundException`）；不影响 Android 编译。

---

## 验证状态

```bash
./gradlew :core:database:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :core:database:compileDebugKotlinAndroid        # BUILD SUCCESSFUL
./gradlew :core:network:compileKotlinIosSimulatorArm64    # BUILD SUCCESSFUL
./gradlew :domain:compileKotlinIosSimulatorArm64          # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                              # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.3a | `:core:network` + 订阅 Ktor | ✅ committed |
| D.3b | Kotlin 2.1 + Room iOS | ✅（待 commit） |
| D.3c | ViewModel OkHttp → Ktor（延迟测试等） | ⬜ 下一步 |
| D.3d | Koin 4.1+ bump（可选） | ⬜ |

---

## 下一步（D.3c）待办清单

1. **延迟测试 / SOCKS 代理 OkHttp → Ktor**
   - `XrayViewmodel` / `SettingsViewmodel` 中的 OkHttp 调用
   - 在 `:core:network` 新增 `NodeDelayTester` 或扩展 `SubscriptionFetcher`
2. **`:app` 移除剩余 OkHttp 依赖**（若 D.3c 全部迁完）
3. **（可选）Entity mapper 迁入 `:core:database` 或 `:domain`**
4. **（可选）Koin 4.1.1+ bump** —— Kotlin 2.1.10 已满足前置

**验证命令**：
```bash
./gradlew :core:network:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
# 手动：延迟测试、订阅刷新、VPN 连接
```

---

## Commit 建议

```
feat(kmp): upgrade Kotlin 2.1.10 and enable Room iOS in core:database (D.3b)
```
