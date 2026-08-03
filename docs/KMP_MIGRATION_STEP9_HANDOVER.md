# KMP 迁移交接文档 — Step 9（2026-08-03）

本文档记录阶段 B 第 5 项：`SettingsViewmodel` geo 下载/导入路径统一走 `XrayAssetPaths`，
与 Step 8 Application 冷启动复制对称，消除设置页对 `context.filesDir` 的直接依赖。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**

---

## 改动概要

**目标**：设置页 geo 文件下载、从 URI 导入时的目标磁盘路径，全部经 `XrayAssetPaths` 提供；
Android 仍写 `filesDir`，iOS 后续 actual 写 App Group，业务逻辑（URL、进度、hash、重启服务）不变。

### 改动清单

**修改**
- `SettingsViewmodel.kt`：构造函数注入 `XrayAssetPaths`
- 私有 `download(url, targetPath, ...)`：`File(targetPath)` 替代 `File(context.filesDir, target)`
- `download(@GEOFileType)`：按类型映射 `geoIpPath` / `geoSitePath` / `geoLiteDatabasePath`
- `onSelectFile()`：IP/SITE 导入改 `assetPaths.geoIpPath` / `geoSitePath`
- `SettingsViewmodelFactory`：注入并传递 `XrayAssetPaths`（`GlobalModule` 已有 `@Binds`，无需改 Module）

**未改（刻意保留）**
- 公开方法 `downloadGeoIP(context)` 等仍保留 `Context` 参数 —— UI 层签名不变，Context 仍用于 Toast / ContentResolver
- parser 包 `javax.inject` —— 留 Step 10（9b），本步不做

**grep 复查（Step 9 验收）**
- `SettingsViewmodel` **零 `filesDir` / `GEO_IP` / `GEO_SITE` / `GEO_LITE` 直引用** ✅
- geo 三种资产路径 **Application（Step 8）+ SettingsViewmodel（Step 9）均已接口化** ✅
- 下载 URL、进度 StateFlow、hash 校验、VPN 重启触发 **未改动** ✅

**仍属 Android 绑定（后续阶段）**
- `XrayCoreManager` 中 `context.filesDir`（XUDP 密钥路径）—— 阶段 C 平台抽象
- parser 包 `javax.inject` —— Step 10
- `SettingsRepository` 中 `android.util.Log` —— Step 11 或 Kermit
- `onSelectFile` / `getFileName` 中 `ContentResolver` —— iOS 换 DocumentPicker actual

---

## 设计决策

1. **注入 `XrayAssetPaths` 而非在 ViewModel 内构造 `AndroidXrayAssetPaths`** —— 与 parser、Application 共用
   Dagger 单例，路径来源一致。
2. **私有 `download` 参数从 `(target: String, context)` 改为 `(targetPath: String)`** —— 文件名常量
   不再经 ViewModel 传递；路径解析集中在 `XrayAssetPaths` 实现。
3. **公开 download 方法保留 `Context` 参数** —— 避免改动 Compose UI 调用点；Context 仅用于
   并发 guard 之外的 UI 边界（本方法内已不再用于拼路径）。
4. **Step 9 仅做 9.1（SettingsViewmodel）** —— parser 去 `javax.inject` 影响 7 个文件 + Factory，
   单独 PR 更易 review，见 Step 10。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCore` 接口 | ✅ |
| Step 6 | `GeoIpProvider` + `XrayAssetPaths`（GeoLite） | ✅ |
| Step 7 | SettingsRepository DataStore 平台化 | ✅ |
| Step 8 | `XrayAssetPaths` 扩展 + Application 初始化 | ✅ |
| Step 9 | SettingsViewmodel geo 路径接口化 | ✅ |
| Step 10 | parser 消除 `javax.inject` | ⬜ |

---

## 下一步（Step 10）待办清单

### 10.1 parser 包消除 `javax.inject`

**目标**：parser 实现类不再依赖 Dagger 注解，为迁入 `domain` KMP 模块或切换 Koin 做准备。

**建议步骤**：
1. `ParserFactory` 改为构造函数接收 parser 列表或显式参数（无 `@Inject`）
2. 7 个 parser 去掉 `@Inject` / `@Singleton`，由 Dagger Module `@Provides` 或 Koin `factory` 组装
3. 或：parser 迁入 `:common` / 未来 `:domain`，仅保留 Android Module  wiring

**涉及文件（预估）**：
- `app/.../parser/*.kt`（7 个 parser + `ParserFactory` + `AbstractConfigParser`）
- `GlobalModule.kt` 或新建 `ParserModule`

**注意点**：`GeoIpProvider` 注入方式不变；仅移除 parser 类上的 JSR-330 注解

### 10.2 后续

| 步骤 | 内容 | 关键文件 |
|------|------|----------|
| Step 11 | `SettingsRepository` 去除 `android.util.Log` | `SettingsRepository.kt` |
| Step 12 | `XrayCoreManager` filesDir 路径抽象 | `XrayCoreManager.kt` |
| 阶段 C | Gson → kotlinx.serialization | `model/` ~24 文件 |

**Step 10 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
# 手动：各协议节点解析、订阅导入仍正常
```

---

## 阶段 C/D 预览（更远，Step 10 不做）

- Dagger → Koin；Room/DataStore/OkHttp → KMP 版本
- `:common` 转 multiplatform plugin
- iOS `XrayAssetPaths` actual + DocumentPicker 导入 geo 文件
