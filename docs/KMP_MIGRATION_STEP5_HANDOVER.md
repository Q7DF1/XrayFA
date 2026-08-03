# KMP 迁移交接文档 — Step 5（2026-08-03）

本文档记录阶段 B 第 1 项：`XrayCoreManager` → `XrayCore` 平台接口，
将 `libv2ray` 调用收敛到单一 Android 实现类。

验证状态：
- `./gradlew :common:compileDebugKotlin` **BUILD SUCCESSFUL**
- `./gradlew :common:testDebugUnitTest` **全部通过**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**（本环境 libv2ray AAR 已就绪）

---

## 改动概要

**目标**：共享层只依赖 `XrayCore` 接口，native 绑定（`libv2ray`）仅存在于
`XrayCoreManager` 一处，为后续 `core/native-bridge` 模块的 `expect/actual` 铺路。

### 改动清单

**新增（`common` 模块，零 libv2ray / Android VPN 依赖）**
- `common/.../core/CoreStartOptions.kt`：平台中立启动参数（`url` / `preUrl` / `nextUrl`）
- `common/.../core/TrafficDetector.kt`：自 app 迁入（仅 `SharedFlow` + 两个方法）
- `common/.../core/XrayCore.kt`：extends `TrafficDetector`；
  声明 `startXrayCore` / `stopXrayCore` / `measureDelaySync` / `measureOutboundDelay`

**修改**
- `common/build.gradle.kts`：添加 `kotlinx-coroutines-core`（`TrafficDetector.trafficFlow` 需要）
- `XrayCoreManager.kt`：实现 `XrayCore`；新增 `measureOutboundDelay`（原 ViewModel 直接调 Libv2ray）；
  `startXrayCore` 参数改为 `CoreStartOptions`，内部转回 `StartOptions` 供 parser 使用（逻辑不变）
- `StartOptions.kt`：新增 `toCoreStartOptions()` 扩展（app 边界转换，保留 `Parcelable`）
- `XrayBaseService.kt` / `XrayViewmodel.kt` / `XrayViewmodelFactory`：注入类型改为 `XrayCore`
- `XrayBaseServiceManager.kt`：`TrafficDetector` import 改走 common
- `GlobalModule.kt`：新增 `@Binds bindXrayCore(XrayCoreManager): XrayCore`

**删除**
- `app/.../core/TrafficDetector.kt`（已迁入 common）

**grep 复查（Step 5 验收）**
- app 模块 Kotlin 源码中 **`libv2ray` import 仅剩 `XrayCoreManager.kt`** ✅
- `XrayViewmodel.kt` 已无 `Libv2ray` 引用 ✅

---

## 设计决策

1. **`CoreStartOptions` 与 `StartOptions` 并存** —— `StartOptions` 需 `Parcelable` 跨 Intent 传递，
   属 Android 边界；接口层用纯 data class，KMP 时可直接进 `commonMain`。
2. **`XrayCoreManager` 内部仍构造 `StartOptions` 调 parser** —— parser 签名尚未迁移，
   在实现类做转换，避免本步扩大 parser 改动面。
3. **`measureOutboundDelay` 不做额外 try/catch** —— 与原 `Libv2ray.measureOutboundDelay` 直调一致；
   ViewModel 侧 `<= 0 → -2` 与 `catch → -2` 映射保持不变。
4. **`startXrayCore` 中 `tunFd?.let { ... }` 结构未动** —— 包括 `tunFd = 0`（hexTun）与
   `tunFd = fd`（常规 VPN）的原有分支语义。
5. **KMP 迁移路径**：`XrayCore` / `TrafficDetector` / `CoreStartOptions` 已是 common 可编译子集；
   后续 `XrayCoreManager` → `androidMain` actual，`iosMain` 用 gomobile xcframework 实现同一接口。

---

## 阶段 B 进度

| 步骤 | 内容 | 状态 |
|------|------|------|
| Step 5 | `XrayCoreManager` → `XrayCore` 接口 | ✅ |
| Step 6 | `Device.getCountryISOFromIp` → `GeoIpProvider`；parser 消除 `xrayPATH` 全局状态 | ⬜ |
| Step 7 | `SettingsRepository` DataStore 创建下沉平台层 | ⬜ |

---

## 下一步（Step 6）待办清单

### 6.1 `GeoIpProvider` 接口化

**目标**：`Device.getCountryISOFromIp` 依赖 MaxMind GeoIP2（JVM/Android 库），
parser 路由规则需要国家码但不应在 common/domain 层直接绑 MaxMind。

**建议步骤**：
1. 在 `common/.../core/GeoIpProvider.kt` 定义 `fun countryIsoFromIp(ip: String): String?`
2. `Device.kt` 或新建 `AndroidGeoIpProvider` 实现，保留现有 MMDB 读取逻辑
3. parser / 路由模块注入 `GeoIpProvider`，移除对 `Device` 静态方法的直接调用

**涉及文件（预估）**：
- `app/.../utils/Device.kt`
- parser 包内引用 `getCountryISOFromIp` 的文件
- `GlobalModule.kt`（bind）

### 6.2 消除 parser 对 `XrayAppCompatFactory.xrayPATH` 的全局状态依赖

**目标**：parser 写 geo/rule 文件时依赖 Application 级静态路径，阻碍 KMP 单测与 iOS 复用。

**建议步骤**：
1. 定义 `XrayAssetPaths` 或 `PlatformPaths` 接口（`geoipPath`、`geositePath` 等）
2. `XrayAppCompatFactory` 提供 Android 实现
3. parser / `AbstractConfigParser` 改为构造函数注入，删除静态字段读取

**注意点**：
- 文件路径在 Android 为 `context.filesDir`，iOS 为 App Group / Documents
- 不改 parser 输出 JSON 内容与文件布局

### 6.3 后续（Step 7，本步不做）

- `SettingsRepository` 的 `dataStore` 扩展属性（`Context.dataStore`）下沉到平台 DataStore 工厂
- 为 DataStore KMP 迁移做准备

**Step 6 验证命令**：
```bash
./gradlew :common:compileDebugKotlin :common:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

---

## 阶段 C/D 预览（更远，Step 6 不做）

- Gson → kotlinx.serialization（`model/` ~24 文件）
- Dagger → Koin；Room/DataStore/OkHttp → KMP 版本
- 建 `core:native-bridge` KMP 模块，将 `XrayCore` actual 从 app 迁入 `androidMain`
- `:common` 转 multiplatform plugin
