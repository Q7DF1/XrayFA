# XrayFA KMP 跨平台迁移方案

## 概述

本文档详细规划了 XrayFA 从 Android-only Compose 项目迁移至 Kotlin Multiplatform (KMP) 项目的完整策略，目标平台为 **Android + iOS**。

**迁移策略**: 自底向上、增量迁移（Strangler Fig 模式）  
**预估总工期**: 19-25 周（1 名开发者）  
**风险等级**: 中高（主要来自原生库集成和 VPN 平台 API 差异）

---

## 当前项目现状分析

### 模块结构
| 模块 | 说明 | 代码量 |
|------|------|--------|
| `:androidApp` | 主应用（Compose UI, VPN 服务, ViewModel） | ~5160 LOC |
| `:common` | 共享工具类 | ~150 LOC |
| `:tun2socks` | Native TUN2Socks JNI 封装 | ~100 LOC |
| `AndroidLibXrayLite/` | Xray-core Go 绑定（git submodule） | 外部 |

### 核心技术栈
- **语言/编译**: Kotlin 2.0.21, AGP 8.10.0, KSP 2.0.21-1.0.27
- **UI**: Jetpack Compose (BOM 2026.03.00), Material 3, Navigation3 (beta)
- **DI**: Dagger 2.57.1 + dagger-android
- **数据库**: Room 2.7.0 (4 次 migration, 3 个 Entity)
- **设置存储**: DataStore Preferences 1.1.7
- **网络**: OkHttp 4.12.0
- **序列化**: Gson 2.11.0
- **相机/二维码**: CameraX + ZXing
- **原生**: GoMobile (Go→Android), NDK (C/C++, tun2socks)
- **SDK**: minSdk 28, targetSdk 36, Java 11

### 代码可共享性分析

**可直接共享 (~40-50%)**:
- 数据模型 (DTO/Model, ~30 个类)
- 协议解析器 (VLESS, VMess, Trojan, Shadowsocks, Hysteria2)
- 订阅解码逻辑
- 路由规则引擎
- Xray 配置构建器
- Repository 接口
- 工具类 (校验, 清理, 解析)

**需平台适配 (~50-60%)**:
- VpnService / NEPacketTunnelProvider
- CameraX QR 扫描 / AVFoundation
- Dagger DI → Koin
- Room → Room KMP
- 通知/前台服务
- Quick Settings Tile / iOS Widget
- 原生库桥接 (JNI / xcframework)
- 剪贴板、权限系统

---

## 技术决策矩阵

| 关注点 | 当前方案 | KMP 目标方案 | 选型理由 |
|--------|----------|-------------|----------|
| **依赖注入** | Dagger 2.57.1 | **Koin 4.x** | KMP 原生支持，无注解处理，迁移成本低。**当前实际锁定 4.0.1**（Kotlin 2.0.21 + iOS klib 兼容）；见 Step 21「Koin 版本决策」，Kotlin 升级后再 bump |
| **数据库** | Room 2.7.0 | **Room KMP** | Room 2.7+ 已支持 KMP，可保留现有 migration 和 DAO |
| **设置存储** | DataStore Prefs | **DataStore KMP** | 官方自 1.1.0 起支持多平台，迁移量极小 |
| **网络请求** | OkHttp 4.12 | **Ktor 3.x** | KMP 原生 HTTP 客户端，各平台独立引擎 |
| **序列化** | Gson | **kotlinx.serialization** | 编译时生成，无反射，KMP 原生支持 |
| **UI 框架** | Jetpack Compose | **Compose Multiplatform** | 最大化代码共享，2026 年 iOS 支持已成熟 |
| **导航** | Navigation3 (beta) | **Decompose 3.x** | 生命周期感知，KMP 原生，稳定且可测试 |
| **ViewModel** | AndroidX ViewModel | **Decompose Component** | 由 Decompose 管理生命周期，共享业务逻辑 |
| **二维码** | CameraX + ZXing | **expect/actual** | 相机 API 天然平台相关，QR 生成可共享 |
| **原生库** | JNI (Go, C) | **expect/actual + xcframework** | Go 可通过 gomobile 编译为 xcframework |
| **日志** | Logcat | **Kermit** (Touchlab) | KMP 日志库，平台后端自动适配 |
| **时间处理** | java.time | **kotlinx-datetime** | KMP 原生时间库 |

---

## 目标模块架构

```
xrayfa/
├── build-logic/                          # 构建约定插件
│   └── convention/
│       ├── kmp-library.gradle.kts        # KMP 库模块约定
│       ├── kmp-compose.gradle.kts        # KMP + Compose 模块约定
│       └── android-app.gradle.kts        # Android 应用约定
│
├── core/                                 # 核心基础层
│   ├── model/                            # 纯 Kotlin 数据模型
│   │   └── src/commonMain/              #   ~30 个 Xray 配置模型, DTO
│   │
│   ├── common/                           # 共享工具/扩展/常量
│   │   ├── src/commonMain/
│   │   ├── src/androidMain/
│   │   └── src/iosMain/
│   │
│   ├── database/                         # Room KMP 数据库
│   │   ├── src/commonMain/              #   Entity, DAO, Migration
│   │   ├── src/androidMain/             #   Android SQLite 驱动
│   │   └── src/iosMain/                 #   iOS SQLite 驱动 (BundledSQLite)
│   │
│   ├── datastore/                        # DataStore KMP 偏好设置
│   │   ├── src/commonMain/              #   Keys, PreferencesDataSource
│   │   ├── src/androidMain/             #   Android DataStore 工厂
│   │   └── src/iosMain/                 #   iOS DataStore 工厂
│   │
│   ├── network/                          # Ktor 网络层
│   │   ├── src/commonMain/              #   API 接口, SubscriptionFetcher
│   │   ├── src/androidMain/             #   OkHttp 引擎
│   │   └── src/iosMain/                 #   Darwin 引擎
│   │
│   └── native-bridge/                    # 原生库桥接
│       ├── src/commonMain/              #   expect 声明 (XrayBridge, TunBridge)
│       ├── src/androidMain/             #   JNI actual 实现
│       └── src/iosMain/                 #   xcframework actual 实现
│
├── domain/                               # 纯业务逻辑层
│   ├── src/commonMain/
│   │   ├── parser/                      #   协议解析器 (5 种协议)
│   │   ├── subscription/                #   订阅解码/导入
│   │   ├── config/                      #   Xray 配置构建器
│   │   ├── routing/                     #   路由规则引擎
│   │   └── repository/                  #   Repository 接口定义
│   └── src/commonTest/                  #   全平台单元测试
│
├── feature/                              # 功能模块（共享 UI + 展示逻辑）
│   ├── home/                            #   首页/连接控制
│   ├── nodes/                           #   节点列表/添加/编辑
│   ├── subscriptions/                   #   订阅管理
│   ├── settings/                        #   设置页面
│   ├── logs/                            #   日志查看器
│   └── qrcode/                          #   二维码扫描/生成
│       ├── src/commonMain/              #     共享 QR 生成逻辑
│       ├── src/androidMain/             #     CameraX 扫码
│       └── src/iosMain/                 #     AVFoundation 扫码
│
├── platform/                             # 平台服务抽象
│   ├── vpn/                             #   VPN 控制器
│   │   ├── src/commonMain/              #     VpnController 接口, VpnState
│   │   ├── src/androidMain/             #     VpnService 实现
│   │   └── src/iosMain/                 #     NEPacketTunnelProvider 桥接
│   ├── notification/                    #   通知控制器
│   └── system/                          #   剪贴板, 权限, 应用生命周期
│
├── shared/                               # KMP 伞模块 (iOS Framework 导出)
│   └── src/
│       ├── commonMain/                  #   汇聚导出 domain + feature
│       ├── androidMain/
│       └── iosMain/                     #   iOS 特有 Kotlin 胶水代码
│
├── androidApp/                           # Android 应用壳 (:androidApp)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── XrayFAApplication.kt         #   Koin 初始化
│       ├── MainActivity.kt              #   入口
│       └── service/                     #   VpnService, TileService, BootReceiver
│
├── iosApp/                               # iOS 应用壳 (Xcode)
│   ├── iosApp/
│   │   ├── ContentView.swift            #   SwiftUI → ComposeUIViewController (E.6+)
│   │   └── Info.plist
│   ├── PacketTunnel/                    #   Network Extension Target
│   │   ├── PacketTunnelProvider.swift
│   │   └── Info.plist
│   └── iosApp.xcodeproj/
│
└── AndroidLibXrayLite/                   # Git Submodule (扩展 iOS 构建目标)
```

---

## 分阶段迁移计划

### Phase 0: 基础设施准备（第 1-2 周）

#### 目标
- 搭建 KMP 构建基础设施，不改动任何运行时代码
- 验证工具链端到端可用性
- 提前排除关键阻塞风险

#### 任务清单

**0.1 构建约定插件 (build-logic)**
```kotlin
// build-logic/convention/src/main/kotlin/kmp-library.gradle.kts
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
            }
        }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = project.name
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

**0.2 版本目录更新 (libs.versions.toml)**
```toml
[versions]
kotlin = "2.1.0"                    # 升级以获得更好的 KMP 支持
compose-multiplatform = "1.7.0"     # Compose Multiplatform
ktor = "3.1.0"
koin = "4.1.0"                      # 目标版本；当前实际 4.0.1，见 Step 21「Koin 版本决策」
decompose = "3.2.0"
kotlinx-serialization = "1.7.3"
kotlinx-datetime = "0.6.2"
kotlinx-coroutines = "1.9.0"
room = "2.7.0"                      # 已支持 KMP
datastore = "1.1.7"                 # 已支持 KMP
kermit = "2.0.5"
sqlite-bundled = "2.5.0"
```

**0.3 关键验证项**
- [ ] 验证 `gomobile bind -target ios` 能成功编译 AndroidLibXrayLite 为 xcframework
- [ ] 升级 Kotlin 至 2.1.x，确认 Android 应用仍可正常编译运行
- [ ] 添加 KMP Gradle 插件，创建空的 `core:model` 模块并验证双平台编译
- [ ] 配置 CI 中的 iOS 模拟器测试目标

**0.4 Apple 开发者准备（优先级最高）**
- [ ] 申请 Apple Developer VPN 权限（审批周期长，必须最先启动）
- [ ] 配置 App Group、Network Extension 等 Entitlement

#### 风险评估
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Kotlin 版本升级破坏现有代码 | 中 | 先修复所有 deprecation warning，升级后全量测试 |
| gomobile xcframework 编译失败 | 高 | 最早验证；准备使用预编译二进制的备选方案 |
| Apple VPN 权限申请被拒 | 致命 | Phase 0 即发起申请，准备详细用例说明 |

#### 交付物
- 项目可成功应用 KMP 插件编译空模块
- iOS Framework 构建成功（空壳）
- Xray-core xcframework 编译验证通过
- CI 双平台绿灯

---

### Phase 1: 提取纯领域层（第 3-5 周）

#### 目标
- 将所有可共享的业务逻辑提取到 `commonMain`
- 实现首次跨平台编译（Android + iOS target）
- Android 应用运行时行为零变化

#### 任务清单

**1.1 创建 `core:model` 模块**

迁移所有无 Android 依赖的数据类：

```kotlin
// core/model/src/commonMain/kotlin/com/xrayfa/model/

// 从 Gson @SerializedName 迁移到 kotlinx.serialization @SerialName
@Serializable
data class XrayConfiguration(
    val log: LogObject? = null,
    val inbounds: List<InboundObject> = emptyList(),
    val outbounds: List<OutboundObject> = emptyList(),
    val routing: RoutingObject? = null,
    val dns: DnsObject? = null,
    // ...
)

// 协议模型
@Serializable
data class OutboundObject(
    val tag: String,
    val protocol: String,
    val settings: JsonObject? = null,
    val streamSettings: StreamSettingsObject? = null,
    // ...
)

// 领域模型 (来自 dto/)
data class Node(val id: Long, val name: String, val protocol: Protocol, val link: String, ...)
data class Subscription(val id: Long, val name: String, val url: String)

enum class Protocol { VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA2 }
```

**迁移模式（逐个类）**:
1. 复制类到 `core:model/src/commonMain`
2. 替换 `@SerializedName` → `@SerialName`，添加 `@Serializable`
3. 在原始位置添加 `typealias` 指向新位置
4. 所有引用更新后删除 typealias

**涉及文件**: `app/src/main/java/com/android/xrayfa/model/` 下全部 ~20 个文件，`dto/` 下 4 个文件

**1.2 Gson → kotlinx.serialization 迁移**

```kotlin
// 迁移前 (Gson)
val gson = Gson()
val config = gson.fromJson(json, XrayConfiguration::class.java)

// 迁移后 (kotlinx.serialization)
val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
val config = json.decodeFromString<XrayConfiguration>(jsonStr)
```

**策略**: 创建适配层使两者在迁移期间共存，逐步替换。

**1.3 创建 `domain` 模块**

提取纯业务逻辑（无框架依赖）：

```kotlin
// domain/src/commonMain/kotlin/com/xrayfa/domain/

// 协议解析器接口
interface ProtocolParser {
    fun canParse(uri: String): Boolean
    fun parse(uri: String): Result<Node>
    fun encode(node: Node): String
}

// 5 个解析器实现: VlessParser, VmessParser, TrojanParser,
// ShadowsocksParser, Hysteria2Parser
// (来自 app/.../parser/ 目录，均为纯字符串解析逻辑)

// 订阅解码器
class SubscriptionDecoder {
    fun decode(content: String): List<String>      // Base64 解码 + 按行分割
    fun parseLinks(links: List<String>): List<Node> // 批量解析链接
}

// Xray 配置构建器
class XrayConfigBuilder {
    fun buildConfig(node: Node, routing: RoutingPrefs, dns: DnsPrefs): XrayConfiguration
}

// Repository 接口
interface NodeRepository {
    fun getAllNodes(): Flow<List<Node>>
    suspend fun insertNode(node: Node): Long
    suspend fun deleteNode(id: Long)
}
```

**涉及文件**: `app/.../parser/` (7 文件), `app/.../repository/` (2 文件)

**1.4 创建 `core:common` 模块**

```kotlin
// expect/actual 示例
expect fun generateUUID(): String

// androidMain
actual fun generateUUID(): String = java.util.UUID.randomUUID().toString()

// iosMain
actual fun generateUUID(): String = platform.Foundation.NSUUID().UUIDString()
```

**1.5 接回 Android App**

更新 `:androidApp` 依赖使用新共享模块：
```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":domain"))
    // ... 现有 Android 依赖保持不变
}
```

#### 测试策略
```bash
./gradlew :domain:allTests          # JVM + iOS Simulator 双平台运行
./gradlew :core:model:allTests      # 序列化往返测试
./gradlew :androidApp:assembleDebug        # Android 回归验证
```

#### 风险评估
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Gson → kotlinx.serialization 破坏 JSON 兼容性 | 高 | 迁移前编写详尽的序列化往返测试，对比输出 |
| Base64 跨平台行为差异 | 低 | 使用 kotlin-stdlib Base64 (Kotlin 1.8+) |
| 正则表达式跨平台差异 | 低 | 双平台测试所有解析正则，避免 lookahead |

#### 交付物
- `core:model`, `core:common`, `domain` 三个模块双平台编译通过
- 所有协议解析器在 iOS Simulator 上通过测试
- Android 应用功能完全不变（回归验证）
- ~25-30% 代码量进入 `commonMain`

---

### Phase 2: 数据层迁移（第 5-8 周）

#### 目标
- Room → Room KMP
- DataStore → DataStore KMP
- OkHttp → Ktor
- Dagger → Koin

#### 任务清单

**2.1 Room KMP 迁移**

Room 2.7+ 原生支持 KMP，主要工作在构建配置：

```kotlin
// core/database/src/commonMain/kotlin/
@Database(
    entities = [NodeEntity::class, SubscriptionEntity::class, LinkEntity::class],
    version = 5,
    autoMigrations = [/* 保留现有 migration */]
)
abstract class XrayDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun linkDao(): LinkDao
}

// 平台工厂
expect fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<XrayDatabase>
```

**关键点**: 现有 4 次 migration 必须保留以支持 Android 用户升级；iOS 新安装直接使用最新 schema。

**2.2 DataStore KMP 迁移**

```kotlin
// core/datastore/src/commonMain/kotlin/
class PreferencesDataSource(private val dataStore: DataStore<Preferences>) {
    val socksPort: Flow<Int> = dataStore.data.map { it[SOCKS_PORT_KEY] ?: 10808 }
    val httpPort: Flow<Int> = dataStore.data.map { it[HTTP_PORT_KEY] ?: 10809 }
    // ...
}

expect fun createDataStore(context: PlatformContext): DataStore<Preferences>
```

**2.3 OkHttp → Ktor**

```kotlin
// core/network/src/commonMain/kotlin/
class SubscriptionFetcher(private val httpClient: HttpClient) {
    suspend fun fetchSubscription(url: String): Result<String>
    suspend fun testNodeDelay(address: String, port: Int): Result<Long>
}

// 各平台引擎
// androidMain: OkHttp engine (保持性能兼容)
// iosMain: Darwin engine
```

**2.4 Dagger → Koin 渐进迁移**

**策略**: 增量替换，双 DI 系统共存过渡
1. 添加 Koin 与 Dagger 并行
2. 逐个组件从 Dagger 迁移到 Koin
3. 全部迁移完成后移除 Dagger

```kotlin
// 共享 DI 模块定义
val domainModule = module {
    factory { VlessParser() }
    factory { VmessParser() }
    // ...
    single<NodeRepository> { NodeRepositoryImpl(get()) }
    single<SubscriptionRepository> { SubscriptionRepositoryImpl(get(), get()) }
}

val databaseModule = module {
    single { getDatabaseBuilder(get()).build() }
    single { get<XrayDatabase>().nodeDao() }
    // ...
}

val networkModule = module {
    single { HttpClient(createHttpEngine()) { /* config */ } }
    single { SubscriptionFetcher(get()) }
}
```

**2.5 平台上下文抽象**

```kotlin
expect class PlatformContext
// androidMain: actual class PlatformContext(val androidContext: Context)
// iosMain: actual class PlatformContext  // iOS 通常不需要全局 context
```

#### 测试策略
- Room: Android 上运行 MigrationTestHelper 测试所有升级路径
- DataStore: 双平台往返读写测试
- Ktor: commonTest 中用 MockEngine 测试
- Koin: `checkModules()` 验证依赖图完整性

#### 风险评估
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Room KMP 迁移破坏现有数据库 | 致命 | 测试每个 schema 版本的升级路径；测试中备份 DB |
| Dagger 移除导致注入图断裂 | 高 | 逐屏幕迁移，保持双 DI 系统临时共存 |
| Ktor 行为与 OkHttp 差异 | 中 | 针对真实订阅端点的集成测试 |

#### 交付物
- 所有数据访问通过 KMP 模块
- Dagger 完全移除，Koin 为唯一 DI 框架
- Android 应用通过全量回归测试
- 数据库迁移在 Android 升级路径上验证通过

---

### Phase 3: 平台抽象与原生桥接（第 8-11 周）

#### 目标
- 定义 VPN、通知、权限等平台抽象接口
- 创建 Xray-core 和 tun2socks 的双平台原生桥接层
- 搭建 iOS Network Extension 骨架

#### 任务清单

**3.1 VPN 平台抽象**

```kotlin
// platform/vpn/src/commonMain/kotlin/

sealed interface VpnState {
    data object Disconnected : VpnState
    data object Connecting : VpnState
    data class Connected(
        val serverAddress: String,
        val connectedAt: Long,
        val bytesIn: Long = 0,
        val bytesOut: Long = 0,
    ) : VpnState
    data object Disconnecting : VpnState
    data class Error(val message: String) : VpnState
}

interface VpnController {
    val state: StateFlow<VpnState>
    suspend fun connect(config: XrayConfiguration): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    fun isVpnPermissionGranted(): Boolean
    suspend fun requestVpnPermission(): Boolean
}
```

**3.2 Android VPN 实现（重构现有代码）**

将现有 `XrayBaseService`, `XrayBaseServiceManager`, `XrayCoreManager` 重构为 `AndroidVpnController` 实现。

**3.3 iOS VPN 实现（NEPacketTunnelProvider）**

```swift
// iosApp/PacketTunnel/PacketTunnelProvider.swift
class PacketTunnelProvider: NEPacketTunnelProvider {
    override func startTunnel(options: [String: NSObject]?,
                             completionHandler: @escaping (Error?) -> Void) {
        // 1. 配置 TUN 网络设置
        // 2. 启动 Xray-core (通过 xcframework)
        // 3. 启动 tun2socks (packet-based)
    }

    override func stopTunnel(with reason: NEProviderStopReason,
                            completionHandler: @escaping () -> Void) {
        // 停止 tun2socks → 停止 Xray-core
    }
}
```

**3.4 原生库桥接层**

```kotlin
// core/native-bridge/src/commonMain/kotlin/
interface XrayBridge {
    suspend fun startXray(configPath: String): Result<Unit>
    suspend fun stopXray(): Result<Unit>
    fun isRunning(): Boolean
    suspend fun measureDelay(config: String, url: String, timeout: Long): Result<Long>
}

interface TunBridge {
    fun startTun2Socks(tunFd: Int, socksAddr: String): Result<Unit>
    fun stopTun2Socks(): Result<Unit>
}
```

- **Android actual**: 使用现有 JNI (LibXrayLite AAR + tun2socks .so)
- **iOS actual**: 使用 gomobile 生成的 xcframework

**3.5 构建 Xray-core 双平台**

```bash
# Android (现有)
gomobile bind -target android -androidapi 28 -o libv2ray.aar ./

# iOS (新增)
gomobile bind -target ios,iossimulator -o LibXrayLite.xcframework ./
```

**3.6 iOS tun2socks 方案选型**

iOS 不使用 TUN fd，而是基于 `NEPacketFlow` 的 packet-based API：
- 方案 A: **hev-socks5-tunnel** (支持 packet-based 模式)
- 方案 B: **Tun2socksKit** (专为 iOS Network Extension 设计)
- 推荐: 方案 B，社区验证充分，与 NEPacketTunnelProvider 集成良好

**3.7 通知和系统服务抽象**

```kotlin
interface NotificationController {
    fun showConnectionNotification(serverName: String)
    fun updateTrafficStats(bytesIn: Long, bytesOut: Long)
    fun cancelConnectionNotification()
}

interface ClipboardManager {
    fun copyToClipboard(text: String)
    fun getFromClipboard(): String?
}
```

#### 风险评估
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| gomobile xcframework 链接失败 | 高 | Phase 0 已验证；保留预编译二进制备选 |
| tun2socks iOS packet-based API 差异 | 高 | 研究 Tun2socksKit/hev-socks5-tunnel |
| NEPacketTunnelProvider 15MB 内存限制 | 致命 | 尽早性能剖析；Go runtime GOGC 调优 |
| App Group 数据共享可靠性 | 中 | 使用 Darwin Notification 变更信号；充分测试 |

#### 交付物
- 平台抽象接口全部定义并有 Android 实现
- iOS Network Extension 骨架可编译链接
- Xray-core xcframework 构建成功
- 原生桥接层双平台验证（至少编译通过）
- Android 应用行为不变

---

### Phase 4: UI 层迁移（第 11-14 周）

#### 目标
- 迁移到 Compose Multiplatform 共享 UI
- 用 Decompose 替换 Navigation3
- 80%+ UI 代码双平台共享

#### 任务清单

**4.1 Decompose 导航架构**

```kotlin
// 根组件 (替代 NavHost)
interface RootComponent {
    val childStack: Value<ChildStack<*, Child>>

    sealed class Child {
        class Home(val component: HomeComponent) : Child()
        class NodeList(val component: NodeListComponent) : Child()
        class Settings(val component: SettingsComponent) : Child()
        class Logs(val component: LogsComponent) : Child()
        class QrScanner(val component: QrScannerComponent) : Child()
        // ...
    }
}
```

**4.2 Component 替代 ViewModel**

```kotlin
// feature/home/src/commonMain/kotlin/
interface HomeComponent {
    val state: Value<HomeState>
    fun onConnectClicked()
    fun onDisconnectClicked()
    fun onNodeSelected(nodeId: Long)
}

data class HomeState(
    val vpnState: VpnState = VpnState.Disconnected,
    val selectedNode: Node? = null,
    val trafficStats: TrafficStats = TrafficStats.EMPTY,
)

class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val vpnController: VpnController,
    private val nodeRepository: NodeRepository,
    private val configBuilder: XrayConfigBuilder,
) : HomeComponent, ComponentContext by componentContext {
    // 业务逻辑实现...
}
```

**4.3 Compose Multiplatform 共享 UI**

```kotlin
// feature/home/src/commonMain/kotlin/ui/HomeScreen.kt
@Composable
fun HomeScreen(component: HomeComponent) {
    val state by component.state.subscribeAsState()
    Scaffold(/* ... */) {
        ConnectionButton(state.vpnState, component::onConnectClicked, component::onDisconnectClicked)
        // ... 共享 UI 组件
    }
}
```

**4.4 平台特定 UI (QR 扫描)**

```kotlin
// expect/actual 分离
@Composable
expect fun QrScannerView(onScanned: (String) -> Unit, modifier: Modifier)

// androidMain: CameraX + ZXing (现有代码)
// iosMain: AVFoundation UIKitView 包装
```

**4.5 应用入口**

```kotlin
// Android: MainActivity
setContent {
    XrayFATheme {
        RootContent(component = rootComponent)
    }
}

// iOS: MainViewController.kt
fun MainViewController(): UIViewController = ComposeUIViewController {
    XrayFATheme {
        RootContent(component = rootComponent)
    }
}
```

```swift
// iOS SwiftUI 包装
struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea(.all)
    }
}
```

#### 风险评估
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Compose Multiplatform iOS 渲染问题 | 中 | 物理设备尽早测试；复杂页面准备 SwiftUI 备选 |
| Material 3 组件 iOS 可用性 | 低 | 使用稳定组件；必要时自定义实现 |
| iOS 性能 vs SwiftUI | 中 | 老设备性能剖析；优化 recomposition |

#### 交付物
- 所有页面通过 Compose Multiplatform 渲染
- Decompose 全面接管导航
- Android 应用视觉与迁移前一致
- iOS 应用可渲染所有页面（可能有粗糙边缘）
- QR 扫描双平台可用

---

### Phase 5: iOS 功能完善与打磨（第 14-18 周）

#### 目标
- 完成 iOS 特有功能
- 处理平台 UX 差异
- App Store 上架准备

#### iOS 功能映射

| Android 功能 | iOS 对应实现 |
|-------------|-------------|
| Quick Settings Tile | WidgetKit Widget |
| Boot Auto-Start | On-Demand VPN Rules (NEOnDemandRule) |
| Foreground Service 通知 | UNUserNotificationCenter |
| Per-App Proxy | `NEVPNProtocol.includeAllNetworks` (受限) |
| Intent 分享导入 | Share Extension |
| Deep Links | Universal Links |

#### iOS 专有实现

**On-Demand VPN (替代开机自启)**:
```kotlin
// 使用 NEOnDemandRuleConnect 实现网络可用时自动连接
fun configureOnDemandRules(manager: NETunnelProviderManager, enabled: Boolean)
```

**App Group 数据共享 (主应用 ↔ Network Extension)**:
```kotlin
// 通过共享 UserDefaults 和 DataStore 文件路径实现进程间通信
private const val APP_GROUP = "group.com.xrayfa"
```

**iOS Widget**:
```swift
// WidgetKit 显示连接状态，点击快速切换
struct VpnStatusWidget: Widget { /* ... */ }
```

#### App Store 上架清单
- [ ] Privacy Manifest (`PrivacyInfo.xcprivacy`)
- [ ] VPN Entitlement (必须 Apple 审批)
- [ ] Network Extension Entitlement
- [ ] App Group Entitlement
- [ ] 加密合规声明 (Export Compliance)
- [ ] 应用图标和启动屏
- [ ] 本地化 (如需)

#### 性能优化重点

**iOS Network Extension 15MB 内存限制**:
```kotlin
// Go runtime 内存限制调优
LibxrayliteSetMemoryLimit(12 * 1024 * 1024) // 12MB
```

#### 风险评估
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Apple VPN 权限审批被拒 | 致命 | Phase 0 已申请；参考同类已通过应用 |
| Network Extension 内存超限 | 致命 | Go runtime 调优；极端情况考虑分离架构 |
| Xray-core 在 Extension 中崩溃 | 高 | 全面错误处理；自动重启逻辑；崩溃上报 |

#### 交付物
- iOS 应用功能完备
- Network Extension 正常工作
- Widget 连接状态可用
- On-Demand VPN 规则正常
- 满足 Apple 审核要求
- 性能在 Network Extension 内存限制内

---

### Phase 6: 集成测试与发布（第 18-20 周）

#### 集成测试矩阵

| 测试用例 | Android | iOS |
|----------|---------|-----|
| VLESS 连接 | ✓ | ✓ |
| VMess 连接 | ✓ | ✓ |
| Trojan 连接 | ✓ | ✓ |
| Shadowsocks 连接 | ✓ | ✓ |
| Hysteria2 连接 | ✓ | ✓ |
| 订阅导入 (URL) | ✓ | ✓ |
| 订阅导入 (QR) | ✓ | ✓ |
| 订阅导入 (剪贴板) | ✓ | ✓ |
| 分应用代理 | ✓ | 受限 |
| 延迟测试 | ✓ | ✓ |
| 日志查看 | ✓ | ✓ |
| 路由规则 | ✓ | ✓ |
| 自动连接 | ✓ | ✓ |
| 快捷开关 | ✓ | ✓ |
| 后台保活 | ✓ | ✓ |
| 数据库升级迁移 | ✓ | N/A |

#### 性能基准

| 指标 | Android 目标 | iOS 目标 |
|------|-------------|----------|
| 冷启动 | < 800ms | < 1000ms |
| 建立连接 | < 3s | < 3s |
| 内存 (应用) | < 80MB | < 60MB |
| 内存 (VPN 进程) | < 50MB | < 15MB |
| 电量 (空闲连接) | < 2%/hr | < 2%/hr |

#### 发布清单

**Android**:
- [ ] 与当前 Play Store 版本回归对比
- [ ] ProGuard/R8 规则覆盖新依赖
- [ ] 崩溃上报集成
- [ ] 发布签名验证

**iOS**:
- [ ] TestFlight 内部测试
- [ ] 外部 Beta 测试 (2 周)
- [ ] App Review 提交
- [ ] 市场截图准备

---

## 阶段 E.6 进度（Config / Settings iOS parity）

| 步骤 | 内容 | 状态 |
|------|------|------|
| E.6o | 共享节点编辑/删除 sheet + iOS Config | ✅ |
| E.6r | iOS Config 轻量创建 sheet（**临时**） | ✅ |
| E.6s | 共享 Route Settings + iOS 接入 | ✅ |
| E.6t | iOS Apps 信息桥接 + allow list | ✅ |
| E.6u | SharedModalBottomSheet 崩溃修复（Dialog 替代 ModalBottomSheet） | ✅ |
| E.6v | **共享完整 EditScreen → iOS 与 Android 统一** | ✅ |
| E.6w | **删除废弃 SharedNodeEditSheet + EditScreen i18n 标签体系** | ✅ |
| E.6x | **Android ConfigUiLabels / ConfigFilterLabels 本地化补全** | ✅ |

---

## 期中后查漏补缺（2026-08-20）

对照 `docs/KMP_MIGRATION_MIDTERM_REVIEW.md`。交接文档仍按 Step 编号维护（STEP73+）。

| 步骤 | 内容 | 状态 |
|------|------|------|
| 73 | `:domain` `commonTest` parser / 配置生成黄金用例 | ✅ `94a88b6` |
| 74 | CI：`kmp-unit-tests.yml` + ios-shared xcframework + android.yml `test` | ✅ `29ca665` |
| 75 | 修正 unpublished `AndroidLibXrayLite` gitlink；submodule URL 保持 SSH | ✅ `6f547fc` `048c36d` |
| 76 | R-2 去重：`EntityMappers` / `RoomNodeRepository` / `KoinQualifiers` / SubscriptionRepository | ✅ `9a95c43` |
| 77 | 断 `:domain → :core:datastore`：路由类型 / `Rule` / `AppJson` 下沉 `:common` | ✅ |
| 78+ | `:core:data`、Android 收敛 `RootContent`、i18n、AGENT 全量重写 | ⬜ |

### EditScreen / 节点创建：产品与技术决策（2026-08-10）

**原则：Android 为参照，iOS 最终对齐 Android，不在 Android 上叠轻量创建入口。**

| 平台 | 创建节点 | 编辑节点 | 说明 |
|------|----------|----------|------|
| **Android** | 顶栏 Edit / 空态添加 → 全屏 `EditScreen`（`Edit` 路由） | 节点行 Edit → `Detail` → 全屏 `EditScreen` | **保持不变**；剪贴板/QR 导入覆盖「粘贴 URL」场景 |
| **iOS（当前）** | 与 Android 相同的全屏共享 `EditScreen` | 与 Android 相同 | E.6v 已完成 |

**E.6v 范围（规划）**：

1. `androidApp/.../EditScreen.kt` + `DetailViewmodel` 表单/保存逻辑 → `shared`（或 `feature:nodes`）
2. 去掉对 Navigation3 / Android `ViewModel` 的绑定；保存走 `NodeRepository` + `ParserFactory`（可扩展 `NodeEditor`）
3. iOS `ConfigTabScreen`：顶栏 Edit / 节点 Edit → 全屏共享 EditScreen（替代轻量 sheet 作为**主路径**）
4. Android：`Edit` / `Detail` 路由改为嵌入共享 EditScreen（Strangler 瘦包装），行为与现网一致
5. iOS 轻量 sheet：共享 EditScreen 落地后可**移除创建入口**；编辑是否保留 sheet 视迁移成本再定

**已知技术债**：shared 模块勿在 commonMain 直接调用 `ModalBottomSheet`、`ExposedDropdownMenuBox`（CMP material3 1.3.1 vs androidApp 1.5.0-alpha15 → `NoSuchMethodError`）；共享 sheet/下拉用 Dialog 模拟直至 CMP/BOM 对齐。

---

## 迁移时间线

```
周:  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20
     ├──┤                                                          Phase 0: 基础设施
        ├────────┤                                                 Phase 1: 领域层
                 ├───────────┤                                     Phase 2: 数据层
                             ├───────────┤                         Phase 3: 平台抽象
                                         ├───────────┤             Phase 4: UI 层
                                                     ├──────────┤  Phase 5: iOS 完善
                                                               ├──┤Phase 6: 发布
```

---

## Android 功能连续性保证

在每个阶段交界点，Android 应用**必须**:
1. 编译运行无崩溃
2. 手动通过所有现有 UI 流程
3. 至少 1 种协议成功连接 VPN
4. 成功导入订阅
5. 正常显示日志

**迁移手法 (Strangler Fig Pattern)**:
1. 创建新 KMP 模块 + 共享接口
2. 在 `androidMain` 中用现有代码实现 actual
3. 将新模块接入 app（替换旧引用路径）
4. 验证通过后才删除旧代码
5. 实现 `iosMain`

---

## 依赖构建顺序图

```
core:model (无依赖)
    ↓
core:common (← model)
    ↓
core:database (← model, common)
core:datastore (← common)
core:network (← model, common)
core:native-bridge (← common)
    ↓
domain (← model, common, database, datastore, network, native-bridge)
    ↓
platform:vpn (← domain, native-bridge)
platform:notification (← common)
platform:system (← common)
    ↓
feature:home (← domain, platform:vpn)
feature:nodes (← domain)
feature:subscriptions (← domain, platform:system)
feature:settings (← domain, platform:vpn)
feature:logs (← domain, platform:system)
feature:qrcode (← domain, platform:system)
    ↓
shared (汇聚: 导出所有 feature + platform)
    ↓
androidApp (← shared + Android 特有)
iosApp (← shared framework)
```

---

## 关键风险总览

| # | 风险 | 概率 | 影响 | 缓解措施 |
|---|------|------|------|----------|
| 1 | Xray-core Go 内存超 iOS NE 15MB 限制 | 高 | 致命 | 尽早性能剖析；GOGC 调优；限制并发；极端情况分离架构 |
| 2 | Apple 拒绝 VPN 权限申请 | 中 | 致命 | Phase 0 即申请；准备详细理由；调研前例应用 |
| 3 | tun2socks iOS 需 packet-based API | 高 | 高 | 使用 Tun2socksKit/hev-socks5-tunnel |
| 4 | Compose Multiplatform iOS 性能不足 | 中 | 中 | 尽早 benchmark；准备 SwiftUI 备选 |
| 5 | Room KMP schema 迁移失败 | 低 | 高 | 详尽迁移测试；保持 schema 不变 |
| 6 | kotlinx.serialization 与 Gson JSON 不兼容 | 中 | 中 | 详尽往返测试；处理边缘情况 |
| 7 | Kotlin/Native 内存模型问题 | 低 | 中 | 使用新内存模型(默认)；避免全局可变状态 |
| 8 | iOS 系统杀死 Network Extension | 中 | 中 | 优雅重启实现；状态持久化到 App Group |

---

## 第一周行动清单

1. [ ] **申请 Apple Developer VPN 权限**（审批周期长，最优先）
2. [ ] 验证 `gomobile bind -target ios` 可成功编译 AndroidLibXrayLite
3. [ ] 升级 Kotlin 至 2.1.x，验证 Android 应用正常
4. [ ] 添加 KMP 插件，创建空 `core:model` 模块验证编译
5. [ ] 配置 CI iOS Simulator 测试目标
6. [ ] 调研 iOS tun2socks 方案 (Tun2socksKit / hev-socks5-tunnel)
7. [ ] 更新 `libs.versions.toml` 添加所有 KMP 新依赖
8. [ ] Profile Xray-core 在 iOS 上的内存使用量，评估 NE 可行性

---

## 工期估算汇总

| 阶段 | 周数 | 信心度 |
|------|------|--------|
| Phase 0: 基础设施准备 | 1.5-2 | 高 |
| Phase 1: 领域层提取 | 2.5-3 | 高 |
| Phase 2: 数据层迁移 | 3-4 | 中高 |
| Phase 3: 平台抽象层 | 3-4 | 中 |
| Phase 4: UI 层迁移 | 3-4 | 中 |
| Phase 5: iOS 功能完善 | 3.5-4.5 | 中低 |
| Phase 6: 集成测试发布 | 2-3 | 中 |
| **总计** | **19-25** | - |

**建议缓冲**: +20% → **实际预期 23-30 周**（1 名开发者）

> 如有 2 名开发者并行（1 人专注 iOS 平台层，1 人专注共享层），可压缩至 **14-18 周**。
