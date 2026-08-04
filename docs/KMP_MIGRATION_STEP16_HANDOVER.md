# KMP 迁移交接文档 — Step 16 / 阶段 C.3（2026-08-04）

本文档记录阶段 C 第 3 项：DataStore 工厂 expect/actual，`SETTINGS_DATA_STORE_NAME`
迁入 `commonMain`，Android 路径与 legacy `preferencesDataStore` 保持一致。

验证状态：
- `./gradlew :common:compileDebugKotlinAndroid` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :common:compileKotlinIosSimulatorArm64` **BUILD SUCCESSFUL**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

**待用户确认后再 commit。**

---

## 改动概要

**目标**：Settings DataStore 创建逻辑 KMP 化；`commonMain` 依赖
`datastore-preferences-core`；Android / iOS 各自 actual 解析文件路径。

**跨平台意义**：iOS 可创建同名 preferences 文件；Android 升级用户文件位置不变。

### 改动清单

**新增（`commonMain`）**
- `datastore/SettingsDataStoreFactory.kt`
  - `SETTINGS_DATA_STORE_NAME = "settings"`（从 `SettingsRepository.kt` 迁出）
  - `expect class SettingsDataStoreContext`
  - `expect fun createSettingsDataStore(...)`

**新增（`androidMain`）**
- `datastore/SettingsDataStoreFactory.android.kt`
  - `PreferenceDataStoreFactory.createWithPath` + `preferencesDataStoreFile`
  - `ConcurrentHashMap` 按 `applicationContext` 单例（等同原 delegate 语义）

**新增（`iosMain`）**
- `datastore/SettingsDataStoreFactory.ios.kt`
  - Documents 目录 + `settings.preferences_pb`（App Group 留 iOS app 壳阶段）

**修改**
- `common/build.gradle.kts`：`datastore-preferences-core` → `commonMain`
- `SettingsRepository.kt`：移除 `SETTINGS_DATA_STORE_NAME`；删除未使用的 `Gson` 注入
- `app/.../SettingsDataStore.kt`：改委托 `createSettingsDataStore(SettingsDataStoreContext(...))`

**未改（刻意）**
- `GlobalModule.provideSettingsDataStore` —— 仍 `context.settingsDataStore`，扩展已走 KMP 工厂
- `XrayFAApplication` —— 仍用 `settingsDataStore` 扩展，与 DI 注入同一实例

**grep 复查（Step 16 验收）**
- Android 路径 **仍用 `preferencesDataStoreFile("settings")`** ✅
- `SETTINGS_DATA_STORE_NAME` **仅在 `commonMain/datastore`** ✅
- `SettingsRepository` **零 Gson 依赖** ✅
- iOS Simulator **编译通过** ✅

---

## 设计决策

1. **常量迁到 `common.datastore`** —— 工厂与文件名同文件，iOS actual 可编译引用。
2. **Android 不用 `by preferencesDataStore`** —— KMP 工厂统一入口；路径 API 不变，升级无感。
3. **`ConcurrentHashMap` 缓存** —— 替代 property delegate 单例；`applicationContext` 为 key。
4. **iOS 暂用 Documents** —— App Group 需 entitlements / iOS app 模块；C.3 只验证 KMP 编译链。
5. **移除 `SettingsRepository` 的 `Gson`** —— C.2 后已无使用；Dagger 仍提供 `Gson` 给 parser。

---

## 阶段 C 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| C.1 | `:common` 转 KMP multiplatform | ✅ |
| C.2 | Gson → kotlinx.serialization（Rule + String list） | ✅ |
| C.3 | DataStore KMP | ✅（待 commit） |
| C.4 | parser + model 物理迁入 `:domain` | ⬜ |

---

## 下一步（C.4）待办清单

### C.4 parser + model 物理迁入 `:domain`

**依赖**：C.2 序列化试点、common 已 KMP、C.3 DataStore 工厂

**目标**：将 `app/.../parser/` 与无 Android 依赖的 model 迁入新 KMP `:domain` 模块。

**验证命令**：
```bash
./gradlew :common:compileDebugKotlinAndroid :common:testDebugUnitTest
./gradlew :domain:compileKotlinIosSimulatorArm64   # 新模块配置后
./gradlew :app:compileDebugKotlin
```

---

## 阶段 D 预览（阶段 C 不做）

- Dagger → Koin
- Compose Multiplatform UI
- iOS VPN Network Extension
