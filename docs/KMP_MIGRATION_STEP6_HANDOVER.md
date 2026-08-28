# KMP 迁移交接文档 — Step 6（2026-08-03）

本文档记录阶段 B 第 2 项：`GeoIpProvider` / `XrayAssetPaths` 平台接口化，
消除 parser 对 `XrayAppCompatFactory.xrayPATH` 全局可变状态的依赖。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：parser 层不再直接读 Application 级静态路径或 MaxMind JVM API，
改为注入平台接口；Android 实现集中在 `AndroidGeoIpProvider` / `AndroidXrayAssetPaths`。

### 改动清单

**新增（`common` 模块，零 MaxMind / Android 依赖）**
- `common/.../core/GeoIpProvider.kt`：`countryIsoFromIp(ip)` 接口
- `common/.../core/XrayAssetPaths.kt`：`geoLiteDatabasePath` 接口

**新增（Android 实现，唯一 MaxMind 引用点）**
- `app/.../core/AndroidGeoIpProvider.kt`：自 `Device.getCountryISOFromIp` 迁入 MMDB 逻辑
- `app/.../core/AndroidXrayAssetPaths.kt`：`context.filesDir` + `GEO_LITE` 常量

**修改**
- `AbstractConfigParser.kt`：新增 `abstract val geoIpProvider` 与 `countryIsoForServer()` 辅助方法
- 7 个 parser（VLESS/VMess/Trojan/SS/Hysteria2/Socks/Http）：注入 `GeoIpProvider`，
  `preParse` 改调 `countryIsoForServer()`，移除 `XrayAppCompatFactory` / `Device` import
- `Device.kt`：仅保留 `getDeviceIdForXUDPBaseKey()`（Xray XUDP 密钥用）
- `XrayAppCompatFactory.kt`：删除 `xrayPATH` 静态可变字段
- `XrayFAApplication.kt`：删除 `xrayPATH = filesDir.absolutePath` 赋值（geo 文件复制逻辑不变）
- `GlobalModule.kt`：新增 `bindGeoIpProvider` / `bindXrayAssetPaths`

**grep 复查（Step 6 验收）**
- parser 包 **零 `XrayAppCompatFactory` import** ✅
- parser 包 **零 `Device.getCountryISOFromIp` 调用** ✅
- app 模块 **MaxMind `DatabaseReader` 仅在 `AndroidGeoIpProvider.kt`** ✅
- `xrayPATH` 全局变量 **已删除** ✅

---

## 设计决策

1. **`countryIsoForServer` 收拢 geoLiteInstall 判断** —— 7 个 parser 原先各自
   `if (geoLiteInstall) Device.get... else ""`；逻辑等价，减少重复。
2. **`AndroidXrayAssetPaths` 直接读 `context.filesDir`** —— 比异步赋值的 `xrayPATH`
   静态变量更稳定（消除潜在的 null / 竞态）；路径格式与原先
   `"${xrayPATH}/${GEO_LITE}"` 完全一致。
3. **MMDB 读取逻辑原样迁移** —— `DatabaseReader` + `InetAddress.getByName` +
   `countryCodeToEmoji` 行为不变；失败仍返回 `""`，未知码仍返回 `❓`。
4. **Trojan parser 保留 `host ?: ""` 语义** —— `countryIsoForServer(trojanConfig.host ?: "")`
   与原 `trojanConfig.host?:""` 一致。
5. **KMP 迁移路径**：`GeoIpProvider` / `XrayAssetPaths` 已是 common 可编译子集；
   iOS actual 分别用 Bundled MMDB + App Group 路径实现。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCoreManager` → `XrayCore` 接口 | ✅ |
| Step 6 | `GeoIpProvider` + `XrayAssetPaths`；消除 `xrayPATH` | ✅ |
| Step 7 | `SettingsRepository` DataStore 创建下沉平台层 | ⬜ |

---

## 下一步（Step 7）待办清单

### 7.1 SettingsRepository DataStore 平台化

**目标**：`common/.../SettingsRepository.kt` 中的 `Context.dataStore` 顶层扩展属性
绑定了 Android `Context`，阻碍 `:common` 转 KMP `commonMain`。

**建议步骤**：
1. 在 `common` 定义 `SettingsDataStoreFactory` 或 `expect fun createSettingsDataStore(...)`
2. Android actual：保留现有 `preferencesDataStore` 扩展 + `Context` 注入
3. `SettingsRepository` 构造函数接收 `DataStore<Preferences>`，不再引用 `Context`
4. `XrayFAApplication` / DI 模块负责创建并注入 DataStore 实例

**涉及文件（预估）**：
- `common/.../repository/SettingsRepository.kt`（及同文件的 `dataStore` 扩展）
- `app/.../XrayFAApplication.kt`
- `app/.../viewmodel/XrayViewmodel.kt`（直接读 `context.dataStore` 的调用点）
- `GlobalModule.kt` 或新建 `DataStoreModule`

**注意点**：
- DataStore 文件名 `"settings"` 与现有用户数据必须保持一致
- `SettingsViewmodel`、`XrayViewmodel` 中 `context.dataStore.data.first()` 需改走 Repository 或注入 DataStore

### 7.2 后续（阶段 B 收尾 / 阶段 C 入口，Step 7 不做）

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 8 | `XrayAssetPaths` 扩展 `geoIpPath` / `geoSitePath`（Application 初始化 geo 文件也接口化） | `XrayFAApplication.kt` |
| Step 9 | parser 包消除 `javax.inject`（随 Dagger→Koin 或 parser 迁入 domain） | 7 个 parser |
| 阶段 C | Gson → kotlinx.serialization（`model/` ~24 文件） | `model/`、`parser/` |

**Step 7 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：设置页读写、主题切换、geo 下载开关仍正常
```

---

## 阶段 C/D 预览（更远，Step 7 不做）

- Dagger → Koin；Room/DataStore/OkHttp → KMP 版本
- 建 `core:native-bridge` KMP 模块
- `:common` 转 multiplatform plugin
