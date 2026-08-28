# KMP 迁移交接文档 — Step 35 / 阶段 D.4b（2026-08-06）

本文档记录阶段 D 第 4 项（第二批）：`SettingsRepository` 迁入 `:core:datastore` commonMain；
`:common` 移除 DataStore 依赖。

**前置**：Step 34 / D.4a 已 commit（`c3d2682`）。

---

## 前置条件检查（D.4b 入口）

| 前置项 | 状态 |
|--------|------|
| D.4a `:core:datastore` 工厂层 | ✅ committed |
| SettingsRepository 纯 DataStore KMP API | ✅（无 Android 专有调用） |

---

## 改动概要

**目标**：设置读写逻辑进入 `:core:datastore` commonMain，iOS 可编译；Android 行为与 Preference keys 不变。

### 迁入 — `:core:datastore` commonMain

| 文件 | 说明 |
|------|------|
| `SettingsRepository.kt` | 含 `SettingsState`、`SettingsKeys`、`DEFAULT_DELAY_TEST_URL` |

**包名**：`com.android.xrayfa.datastore`

### 变更

| 文件 | 变更 |
|------|------|
| `core/datastore/build.gradle.kts` | 依赖 `:common`（Rule/AppJson/Enums/Logger/ConfigParserSettings） |
| `common/build.gradle.kts` | 移除 `datastore-preferences` / `datastore-preferences-core` |
| `tun2socks/build.gradle.kts` | 新增 `implementation(project(":core:datastore"))` |
| `:app` / `:tun2socks` 14 处 | import 改 `com.android.xrayfa.datastore.*` |

### 删除 — `:common`

- `androidMain/.../SettingsRepository.kt`

**仍留 `:common`**：
- `ConfigParserSettings` / `ConfigParserSettingsProvider`（`:domain` parser 依赖）
- `Rule` / `AppJson` / `SettingsEnums`（路由规则 JSON 序列化，parser 共享）

**未改动**：
- 全部 Preference key 名称与默认值
- Koin 绑定：`SettingsRepository(get(), get())` + `ConfigParserSettingsProvider`
- Android DataStore 文件路径（D.4a 工厂层）

---

## 设计决策与原因

1. **Repository 进 commonMain** —— 仅使用 DataStore KMP API + coroutines，无 Android 框架调用。
2. **Rule/AppJson 暂留 `:common`** —— `:domain` parser 已深度依赖；一并迁移会扩大 diff，留 D.5 或后续步。
3. **避免循环依赖** —— `:core:datastore` → `:common`（单向）；不反向依赖。
4. **import 直改 package** —— 不用 typealias，避免 `:common` ↔ `:core:datastore` 环依赖。

---

## 验证状态

```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64   # BUILD SUCCESSFUL
./gradlew :core:datastore:compileDebugKotlinAndroid        # BUILD SUCCESSFUL
./gradlew :domain:testDebugUnitTest                       # BUILD SUCCESSFUL
./gradlew :app:assembleDebug                              # BUILD SUCCESSFUL
```

**待用户确认后再 commit。**

**建议手动回归**：设置页各项读写、路由规则、分应用代理、VPN 连接。

---

## 阶段 D 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| D.4a | `:core:datastore` 工厂层 | ✅ committed |
| D.4b | SettingsRepository 迁入 | ✅（待 commit） |
| D.4c | Rule/AppJson 等设置模型整理（可选） | ⬜ 下一步 |
| D.3d | Koin 4.1+ bump（可选） | ⬜ |

---

## 下一步（D.4c / Phase 2 续）待办清单

1. **（可选）`Rule` / `AppJson` / `SettingsEnums` 迁入 `:core:datastore` 或 `:domain`**
2. **（可选）Entity mapper 迁入 `:core:database`**
3. **清理 `:common` 遗留 Dagger KSP 依赖**（androidMain 已无 `@Inject` 业务代码）
4. **阶段 E 入口**：平台抽象（VPN / native-bridge）或 Compose Multiplatform 壳

**验证命令**：
```bash
./gradlew :core:datastore:compileKotlinIosSimulatorArm64
./gradlew :domain:testDebugUnitTest
./gradlew :app:assembleDebug
```

---

## Commit 建议

```
feat(kmp): move SettingsRepository into core:datastore commonMain (D.4b)
```
