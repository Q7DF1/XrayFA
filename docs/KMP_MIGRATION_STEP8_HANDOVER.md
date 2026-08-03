# KMP 迁移交接文档 — Step 8（2026-08-03）

本文档记录阶段 B 第 4 项：扩展 `XrayAssetPaths` 覆盖 geoip.dat / geosite.dat，
使 `XrayFAApplication.initXrayFile()` 不再直接依赖 `filesDir` 拼路径。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：Application 首次启动从 assets 复制 geo 数据库时，目标路径与 Step 6 已接口化的
GeoLite 路径对称——全部经 `XrayAssetPaths` 提供；Android 仍写 `filesDir`，iOS 后续 actual 写 App Group。

### 改动清单

**修改（`common`）**
- `XrayAssetPaths.kt`：新增 `geoIpPath`、`geoSitePath`（与既有 `geoLiteDatabasePath` 并列）

**修改（Android 实现）**
- `AndroidXrayAssetPaths.kt`：实现 `geoIpPath` / `geoSitePath`（`filesDir` + `GEO_IP` / `GEO_SITE` 常量）

**修改（Application + DI）**
- `XrayFAApplication.kt`：`initXrayFile()` 目标文件改 `File(assetPaths.geoIpPath)` /
  `File(assetPaths.geoSitePath)`；assets 源文件名仍用 `GEO_IP` / `GEO_SITE`（仅读 APK assets，属 Android 边界）
- `XrayFAComponent.kt`：暴露 `xrayAssetPaths(): XrayAssetPaths` 供 Application 在 DI 图建立后取用
- `xrayAssetPaths()` 辅助方法：`rootComponent` 优先，否则 `AndroidXrayAssetPaths(applicationContext)` 兜底（与 Step 7 settingsDataStore 直访模式一致）

**grep 复查（Step 8 验收）**
- `XrayFAApplication.initXrayFile` **零 `filesDir` 直拼 geo 目标路径** ✅
- `XrayAssetPaths` **三种 geo 资产路径均已接口化** ✅
- assets 复制逻辑（exists 判断、流拷贝）**未改动** ✅

**仍属 Android 绑定（后续阶段，Step 8 不做）**
- `SettingsViewmodel` 下载 / 导入 geo 仍 `context.filesDir` + `GEO_IP`/`GEO_SITE` —— Step 9+ 可改注入 `XrayAssetPaths`
- `initXrayFile` 中 `assets.open(...)` —— 读 APK bundled assets，iOS 将换 Bundle 复制逻辑
- `SettingsRepository` 中 `android.util.Log` —— 待 Kermit 或平台日志抽象

---

## 设计决策

1. **三路径同接口、同 Android 实现类** —— `geoIpPath` / `geoSitePath` / `geoLiteDatabasePath`
   格式均为 `"${filesDir.absolutePath}/<filename>"`，与 Step 6 前 `"${xrayPATH}/${GEO_*}"` 及
   原先 `File(filesDir, GEO_IP)` 完全等价。
2. **assets 源名保留 `GEO_IP`/`GEO_SITE` 常量** —— 常量表示 APK assets 内文件名，跨平台共享；
   仅「写入磁盘的目标绝对路径」平台化。
3. **Component 暴露 accessor 而非 Application `@Inject`** —— `onCreate` 内 `onContextAvailable`
   已先于 `initXrayFile` 构建 Dagger 图；accessor 改动最小，无需新增 `inject(Application)`。
4. **`rootComponent` 为空时兜底 `AndroidXrayAssetPaths`** —— 单测或未挂 `ContextAvailableCallback`
   时行为与直接构造一致，不影响生产路径（Factory 必设 callback）。
5. **未动 `SettingsViewmodel`** —— 本步范围仅 Application 冷启动复制；设置页下载/导入路径
   留作下一步小步，避免单 PR 面过大。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCore` 接口 | ✅ |
| Step 6 | `GeoIpProvider` + `XrayAssetPaths`（GeoLite） | ✅ |
| Step 7 | SettingsRepository DataStore 平台化 | ✅ |
| Step 8 | `XrayAssetPaths` 扩展 geoip/geosite + Application 初始化 | ✅ |
| Step 9 | `SettingsViewmodel` geo 路径接口化 + parser 去 `javax.inject` | ⬜ |

---

## 下一步（Step 9）待办清单

### 9.1 SettingsViewmodel geo 文件路径统一走 `XrayAssetPaths`

**目标**：消除设置页下载 / 导入 / 校验中对 `context.filesDir` + 文件名的直接拼接，
与 Step 8 Application 初始化对称。

**建议步骤**：
1. `SettingsViewmodel` 注入 `XrayAssetPaths`
2. `download()` / `onSelectFile()` 目标路径改 `File(assetPaths.geoIpPath)` 等
3. `GEO_LITE` 导入可复用 `assetPaths.geoLiteDatabasePath`

**涉及文件（预估）**：
- `app/.../viewmodel/SettingsViewmodel.kt`
- `GlobalModule` 或 ViewModel Factory（若尚未能解析 `XrayAssetPaths`）

**注意点**：下载 URL、进度 StateFlow、文件 hash 校验逻辑不变；仅替换路径来源

### 9.2 parser 包消除 `javax.inject`（可与 9.1 分 PR）

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 9b | parser 消除 `javax.inject`（随 Dagger→Koin 或 parser 迁入 domain） | 7 个 parser |
| Step 10 | `SettingsRepository` 去除 `android.util.Log` | `SettingsRepository.kt` |
| 阶段 C | Gson → kotlinx.serialization | `model/` ~24 文件 |

**Step 9 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：设置页 geo 下载、从文件导入、首次安装 assets 复制仍正常
```

---

## 阶段 C/D 预览（更远，Step 9 不做）

- Dagger → Koin；Room/DataStore/OkHttp → KMP 版本
- `:common` 转 multiplatform plugin（`XrayAssetPaths` / `GeoIpProvider` 已可进 `commonMain`）
- 建 `core:model` / `core:domain` KMP 模块
- iOS `XrayAssetPaths` actual：App Group 容器路径 + Bundle geo 资产首次复制
