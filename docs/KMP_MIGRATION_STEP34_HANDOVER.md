# KMP 迁移交接文档 — Step 34 / 阶段 D.4a（2026-08-06）

本文档记录阶段 D 第 4 项（第一批）：创建 `:core:datastore` KMP 模块，
迁移 `SettingsDataStoreFactory` expect/actual；清理无用 Koin qualifier。

**前置**：Step 33 / D.3c 已 commit（`86822af`）。

---

## 前置条件检查（D.4a 入口）

| 前置项 | 状态 |
|--------|------|
| D.3c OkHttp 全部迁出 `:app` | ✅ committed |
| Kotlin 2.1.10 + iOS target 可用 | ✅ |
| `:common` 已有 DataStore KMP expect/actual | ✅（本步迁出） |

---

## 改动概要

**目标**：DataStore 平台工厂独立为 KMP 模块；Android 磁盘路径与文件名不变；iOS 可编译。

### 新增模块 `:core:datastore`

| 文件 | 说明 |
|------|------|
| `SettingsDataStoreFactory.kt` | expect 声明 + `SETTINGS_DATA_STORE_NAME` 常量 |
| `SettingsDataStoreFactory.android.kt` | `preferencesDataStoreFile("settings")` — 与原路径一致 |
| `SettingsDataStoreFactory.ios.kt` | Documents 目录 + 单例缓存 |

**包名变更**：`com.android.xrayfa.common.datastore` → `com.android.xrayfa.datastore`

### 变更

| 文件 | 变更 |
|------|------|
| `settings.gradle.kts` | `include(":core:datastore")` |
| `gradle/libs.versions.toml` | 新增 `androidx-datastore-preferences-core` |
| `app/build.gradle.kts` | `implementation(project(":core:datastore"))` |
| `app/.../SettingsDataStore.kt` | import 改指向 `:core:datastore` |
| `AppDataDiModule.kt` | 删除已无用的 `SHORT_TIME` / `LONG_TIME` qualifier |

### 删除 — `:common`

- `common/datastore/SettingsDataStoreFactory.*`（三平台）

**未改动**：
- `SettingsRepository` 仍留 `:common`（下一步 D.4b 可迁入 `:core:datastore` commonMain）
- Android 运行时 DataStore 文件路径、Koin 注入链不变

---

## 设计决策与原因

1. **本步仅迁工厂层** —— 降低 diff 风险；`SettingsRepository` 依赖 `Rule`/`AppJson`/`Logger`，与 `:domain` 耦合，单独一步处理。
2. **包名 `com.android.xrayfa.datastore`** —— 与 `:core:database`、`:core:network` 命名一致。
3. **Android 路径零变化** —— 仍用 `preferencesDataStoreFile(SETTINGS_DATA_STORE_NAME)`，用户升级不丢设置。
4. **清理 `SHORT_TIME`/`LONG_TIME`** —— D.3c 已移除 OkHttp 客户端，qualifier 无引用。

---

## 验证状态

```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :core:datastore:compileDebugKotlinAndroid        # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                              # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.3c | ViewModel OkHttp → Ktor | ✅ committed |
| D.4a | `:core:datastore` 工厂层 | ✅（待 commit） |
| D.4b | `SettingsRepository` 迁入 `:core:datastore` | ⬜ 下一步 |
| D.3d | Koin 4.1+ bump（可选） | ⬜ |

---

## 下一步（D.4b）待办清单

1. **`SettingsRepository` + `SettingsKeys` + `SettingsState` → `:core:datastore` commonMain**
   - 依赖：`Rule`/`AppJson`/`SettingsEnums` 暂留 `:common` 或一并迁移
2. **`:common` 改依赖 `:core:datastore`**
3. **（可选）Entity mapper 迁入 `:core:database`**
4. **（可选）Koin 4.1.1+ bump**

**验证命令**：
```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): extract DataStore factory into core:datastore module (D.4a)
```
