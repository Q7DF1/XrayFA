# KMP 迁移交接文档 — Step 1（2026-08-01）

## 背景

目标：将 XrayFA 从 Android-only 迁移为 KMP（Android + iOS）。总体方案见 `docs/KMP_MIGRATION_PLAN.md`，本文档记录第一阶段实际执行的操作与环境修复。

迁移工作流（由易到难）：

- **阶段 A：零风险解耦**（当前工程内纯重构）
  1. ✅ 清除纯逻辑代码中的 Android import（本次完成）
  2. ⬜ `java.util.Base64` / `android.util.Base64` → `kotlin.io.encoding.Base64`（6 处，见下文清单）
  3. ⬜ `java.net.URI/URLDecoder` → 纯 Kotlin 解析（可抽 `UrlCodec` 工具类）
- **阶段 B：抽接口**
  4. ⬜ `XrayCoreManager` → `XrayCore` 接口（全项目仅 2 个文件触碰 `libv2ray`：`core/XrayCoreManager.kt`、`viewmodel/XrayViewmodel.kt:537`）
  5. ⬜ `Device.getCountryISOFromIp`（MaxMind geoip2，JVM-only）→ `GeoIpProvider` 接口；消除 parser 对 `XrayAppCompatFactory.xrayPATH` 全局可变状态的依赖
  6. ⬜ `SettingsRepository`（DataStore 本身支持 KMP，只需把 `Context.dataStore` 创建下沉到平台层）
- **阶段 C：换 KMP 依赖**
  7. ⬜ Gson → kotlinx.serialization（`model/` 24 个文件，工作量最大）
  8. ⬜ Dagger → Koin（含 `common` 模块的 `javax.inject` qualifier）
  9. ⬜ Room → Room KMP（DAO/Entity 可保留，只改 `Context` 构建处）；OkHttp → Ktor
- **阶段 D：建 KMP 模块**
  10. ⬜ 建 `core:model` / `core:domain` 模块，移入 `commonMain`，验证 Android + iOS 双编译

## 本次代码改动（已验证 `BUILD SUCCESSFUL`）

### 1. `app/src/main/java/com/android/xrayfa/dto/Node.kt`
删除无用的 `import androidx.compose.ui.graphics.Color`。
**作用**：数据库 Entity 不再依赖 Compose UI 库，为 dto 包进入 `commonMain` 扫清障碍。零行为影响。

### 2. `app/src/main/java/com/android/xrayfa/parser/ParserFactory.kt`
- 删除 `android.net.Uri`、`androidx.core.net.toUri` 两个 import
- `url.toUri().scheme` → `url.substringBefore("://").lowercase()`

**作用**：parser 包入口不再依赖 Android 框架类，整个 parser 包（10 个文件、全部协议解析逻辑）向可共享迈进一步。`.lowercase()` 保证与 `Uri.getScheme()` 的大小写语义等价；非法输入仍走 `else` 抛 `IllegalArgumentException`，行为不变。

## 下一步（Step 2）待办清单

统一替换 Base64 为 `kotlin.io.encoding.Base64`（Kotlin 2.0.21 下需 `@OptIn(ExperimentalEncodingApi::class)`），涉及文件：

- `app/.../parser/SubscriptionParser.kt`（`java.util.Base64`）
- `app/.../parser/VMESSConfigParser.kt`（`java.util.Base64`）
- `app/.../parser/SocksConfigParser.kt`（`java.util.Base64`）
- `app/.../parser/ShadowSocksConfigParser.kt`（`java.util.Base64`）
- `app/.../utils/HttpResponseUtils.kt`（`java.util.Base64`）
- `app/.../utils/LinkUtils.kt`（`java.util.Base64`）
- `app/.../utils/Device.kt`（`android.util.Base64`）

**注意点**：逐个确认原代码使用的是标准 / MIME / URL-safe 哪种解码变体，`kotlin.io.encoding.Base64` 默认是 RFC 4648 标准变体（MIME 需 `Base64.Mime`，URL-safe 需 `Base64.UrlSafe`），保证解码行为一致。
