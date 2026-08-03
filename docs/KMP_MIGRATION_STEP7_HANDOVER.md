# KMP 迁移交接文档 — Step 7（2026-08-03）

本文档记录阶段 B 第 3 项：`SettingsRepository` DataStore 创建下沉平台层，
解除 `common` 对 Android `Context` 的 DataStore 绑定。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：`SettingsRepository` 只依赖 `DataStore<Preferences>` 接口，
Android 侧通过 `preferencesDataStore` 扩展创建实例并由 Dagger 注入。

### 改动清单

**新增**
- `common/.../repository/SETTINGS_DATA_STORE_NAME` 常量（值为 `"settings"`，升级兼容）
- `app/.../data/SettingsDataStore.kt`：`Context.settingsDataStore` 扩展（Android 边界）

**修改**
- `SettingsRepository.kt`：构造函数 `Context` → `DataStore<Preferences>`；
  删除 `val Context.dataStore` 扩展及 `preferencesDataStore` import
- `GlobalModule.kt`：新增 `provideSettingsDataStore(@Application context)`
- `XrayFAApplication.kt`：改 import 为 `app.data.settingsDataStore`
- `XrayViewmodel.kt` / `XrayViewmodelFactory`：注入 `SettingsRepository`，
  延迟测试 URL 改读 `settingsFlow.first().delayTestUrl`（与原 DataStore 直读 + 默认值等价）

**grep 复查（Step 7 验收）**
- `common` 模块 **零 `preferencesDataStore` / `Context.dataStore`** ✅
- `SettingsRepository` 构造函数 **不再依赖 `Context`** ✅
- app 层 DataStore 创建 **仅在 `SettingsDataStore.kt` + `GlobalModule`** ✅

**仍属 Android 绑定（后续阶段）**
- `SettingsRepository` 中 `android.util.Log` —— 待 Kermit 或平台日志抽象
- `@IntDef` / `Theme` / `RoutingMode` 注解 —— 可后续迁入纯 Kotlin enum

---

## 设计决策

1. **注入 `DataStore<Preferences>` 而非工厂接口** —— DataStore 已是跨平台抽象；
   Android `@Provides` 足够，KMP 时 iOS actual 同样 `@Provides` 即可。
2. **`SETTINGS_DATA_STORE_NAME` 放 common** —— 文件名约束跨平台共享，防止 iOS actual 误改名。
3. **XrayViewmodel 走 `settingsFlow` 而非注入裸 DataStore** —— 复用 Repository 映射逻辑，
   `delayTestUrl` 默认值与原先 `?: DEFAULT_DELAY_TEST_URL` 一致。
4. **Application 初始化仍直写 DataStore** —— `initSocksConfig` / `initHwid` 在 DI 图建立前运行，
   保留 `settingsDataStore` 扩展直访；与注入实例共享同一 `preferencesDataStore` delegate（单例）。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCore` 接口 | ✅ |
| Step 6 | `GeoIpProvider` + `XrayAssetPaths` | ✅ |
| Step 7 | SettingsRepository DataStore 平台化 | ✅ |
| Step 8 | `XrayAssetPaths` 扩展 geo 文件路径 + Application 初始化接口化 | ⬜ |

---

## 下一步（Step 8）待办清单

### 8.1 扩展 `XrayAssetPaths` 覆盖 geoip.dat / geosite.dat

**目标**：`XrayFAApplication.initXrayFile()` 仍直接用 `filesDir` + `GEO_IP`/`GEO_SITE` 常量，
与 Step 6 仅接口化 GeoLite 路径未完成的 geo 资产初始化对称处理。

**建议步骤**：
1. `XrayAssetPaths` 增加 `geoIpPath`、`geoSitePath`（或 `basePath` + 共用前缀）
2. `AndroidXrayAssetPaths` 实现上述属性
3. `XrayFAApplication` 改注入 `XrayAssetPaths`（或专用 `GeoAssetInitializer`）复制 assets

**注意点**：文件布局与 assets 复制逻辑不变；仅路径来源改为接口

### 8.2 后续（Step 9 / 阶段 C 入口，Step 8 不做）

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 9 | parser 包消除 `javax.inject`（随 Dagger→Koin） | 7 个 parser |
| Step 10 | `SettingsRepository` 去除 `android.util.Log` | `SettingsRepository.kt` |
| 阶段 C | Gson → kotlinx.serialization | `model/` ~24 文件 |

**Step 8 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：首次安装 geoip.dat / geosite.dat 仍从 assets 正确复制
```

---

## 阶段 C/D 预览（更远，Step 8 不做）

- Dagger → Koin；Room/DataStore/OkHttp → KMP 版本
- `:common` 转 multiplatform plugin（`SettingsRepository` 已可进 `commonMain` 子集）
- 建 `core:model` / `core:domain` KMP 模块
