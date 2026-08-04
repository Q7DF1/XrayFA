# 依赖替换详细指南

本文档为 XrayFA KMP 迁移过程中各依赖库的具体替换方案和代码示例。

---

## 1. Gson → kotlinx.serialization

### 迁移对照表

| Gson | kotlinx.serialization |
|------|----------------------|
| `@SerializedName("field")` | `@SerialName("field")` |
| `Gson().fromJson(json, T::class.java)` | `Json.decodeFromString<T>(json)` |
| `Gson().toJson(obj)` | `Json.encodeToString(obj)` |
| `@Expose` | 默认全部序列化，用 `@Transient` 排除 |
| `GsonBuilder()` | `Json { ... }` |
| Runtime 反射 | 编译时生成 |

### 配置

```kotlin
// 全局 Json 实例
val AppJson = Json {
    ignoreUnknownKeys = true    // 忽略未知字段（兼容性）
    encodeDefaults = true       // 编码默认值
    isLenient = true            // 宽松解析
    coerceInputValues = true    // 无效值使用默认值
    prettyPrint = false         // 生产环境不格式化
}
```

### 迁移示例

```kotlin
// 迁移前
data class OutboundObject(
    @SerializedName("tag") val tag: String,
    @SerializedName("protocol") val protocol: String,
    @SerializedName("settings") val settings: Any? = null,
)

val obj = Gson().fromJson(jsonStr, OutboundObject::class.java)

// 迁移后
@Serializable
data class OutboundObject(
    @SerialName("tag") val tag: String,
    @SerialName("protocol") val protocol: String,
    @SerialName("settings") val settings: JsonObject? = null,
)

val obj = AppJson.decodeFromString<OutboundObject>(jsonStr)
```

### 处理动态类型 (Any/Object)

Gson 中常用 `Any` 或 `Object` 类型，kotlinx.serialization 需要明确类型：

```kotlin
// 方案 A: 使用 JsonElement
@Serializable
data class DynamicConfig(
    val settings: JsonElement? = null  // 可以是 JsonObject, JsonArray, JsonPrimitive
)

// 方案 B: 使用多态序列化
@Serializable
sealed interface ProtocolSettings {
    @Serializable
    @SerialName("vless")
    data class VlessSettings(val vnext: List<VnextServer>) : ProtocolSettings

    @Serializable
    @SerialName("vmess")
    data class VmessSettings(val vnext: List<VnextServer>) : ProtocolSettings
}
```

### 注意事项
- kotlinx.serialization 不支持循环引用
- 枚举序列化默认使用 name，若需数字用 `@SerialName`
- `null` 字段默认不输出，需 `explicitNulls = true` 恢复
- `Map` key 必须是 String 或基本类型

---

## 2. Dagger → Koin

### Koin 版本（当前 vs 目标）

| | 版本 | 说明 |
|---|------|------|
| **当前（libs.versions.toml）** | **4.0.1** | Kotlin 2.0.21 下 iOS klib 可编译；Step 21 确认 |
| **目标（Kotlin 升级后）** | 4.1.1+（BOM）或 4.2.2 | 4.1+ 需 Kotlin 2.1+；4.2+ 需 Kotlin 2.3+ |

详见 `docs/KMP_MIGRATION_STEP21_HANDOVER.md` §「Koin 版本决策」。**D.1 DI 迁移不等待 Koin 升级。**

### 概念对照

| Dagger | Koin |
|--------|------|
| `@Component` | `koinApplication { modules(...) }` |
| `@Module` | `module { ... }` |
| `@Provides` | `single { }` / `factory { }` |
| `@Inject constructor()` | 直接实例化或 `get()` |
| `@Singleton` | `single { }` |
| `@Binds` | `single<Interface> { Impl(get()) }` |
| `@Named("x")` | `named("x")` qualifier |
| `@Component.Factory` | Koin 模块参数 |
| `@Subcomponent` | 不需要（扁平化模块结构） |

### 现有 Dagger 模块 → Koin 模块

```kotlin
// 现有 Dagger (di/GlobalModule.kt)
@Module
class GlobalModule {
    @Provides
    @Singleton
    fun provideDatabase(app: Application): XrayFADatabase {
        return Room.databaseBuilder(app, XrayFADatabase::class.java, "xrayfa.db")
            .addMigrations(...)
            .build()
    }

    @Provides
    @Singleton
    fun provideNodeRepository(db: XrayFADatabase): NodeRepository {
        return NodeRepository(db.nodeDao())
    }
}

// 迁移后 Koin
val globalModule = module {
    single<XrayFADatabase> {
        getDatabaseBuilder(get()).build()
    }
    single { get<XrayFADatabase>().nodeDao() }
    single { get<XrayFADatabase>().subscriptionDao() }
    single { get<XrayFADatabase>().linkDao() }
    single<NodeRepository> { NodeRepositoryImpl(get()) }
}
```

```kotlin
// 现有 Dagger (di/NetworkModule.kt)
@Module
class NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }
}

// 迁移后 Koin (core/network)
val networkModule = module {
    single {
        HttpClient(createHttpEngine()) {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
        }
    }
    single { SubscriptionFetcher(get()) }
}
```

```kotlin
// 现有 Dagger (di/CoroutinesModule.kt)
@Module
class CoroutinesModule {
    @Provides
    @Named("io")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Named("main")
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

// 迁移后 Koin
val coroutinesModule = module {
    single(named("io")) { Dispatchers.IO }
    single(named("main")) { Dispatchers.Main }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(named("io"))) }
}
```

### ViewModel/Component 注入

```kotlin
// 现有 Dagger + ViewModel
class XrayViewModel @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() { ... }

// 迁移后 Koin + Decompose Component
class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val nodeRepository: NodeRepository = KoinPlatform.getKoin().get(),
    private val vpnController: VpnController = KoinPlatform.getKoin().get(),
) : HomeComponent, ComponentContext by componentContext { ... }

// 或在 Compose 中直接使用
@Composable
fun HomeScreen(component: HomeComponent = koinInject()) { ... }
```

### 初始化

```kotlin
// Android Application
class XrayFAApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@XrayFAApplication)
            modules(
                globalModule,
                networkModule,
                coroutinesModule,
                domainModule,
                platformModule,
            )
        }
    }
}

// iOS (在 shared module 中)
fun initKoin() {
    startKoin {
        modules(
            globalModule,
            networkModule,
            coroutinesModule,
            domainModule,
            iosPlatformModule,
        )
    }
}
```

---

## 3. OkHttp → Ktor

### API 对照

| OkHttp | Ktor |
|--------|------|
| `OkHttpClient()` | `HttpClient(engine) { }` |
| `Request.Builder().url(url).build()` | `client.get(url) { }` |
| `client.newCall(request).execute()` | `client.get(url)` (suspend) |
| `response.body?.string()` | `response.bodyAsText()` |
| `Interceptor` | `HttpClientPlugin` |
| `addInterceptor(logging)` | `install(Logging)` |
| `RequestBody.create(mediaType, body)` | `setBody(body)` with ContentNegotiation |

### 迁移示例

```kotlin
// 现有 OkHttp 代码
class SubscriptionFetcher(private val client: OkHttpClient) {
    fun fetchSubscription(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        return response.body?.string() ?: throw IOException("Empty body")
    }
}

// 迁移后 Ktor
class SubscriptionFetcher(private val client: HttpClient) {
    suspend fun fetchSubscription(url: String): Result<String> = runCatching {
        client.get(url) {
            header("User-Agent", "XrayFA/${BuildConfig.VERSION_NAME}")
        }.bodyAsText()
    }
}
```

### 延迟测试迁移

```kotlin
// 现有 OkHttp
fun testDelay(address: String, port: Int): Long {
    val start = System.currentTimeMillis()
    val request = Request.Builder()
        .url("http://www.gstatic.com/generate_204")
        .build()
    client.newCall(request).execute()
    return System.currentTimeMillis() - start
}

// 迁移后 Ktor
suspend fun testDelay(proxyAddress: String, proxyPort: Int): Result<Long> = runCatching {
    val start = TimeSource.Monotonic.markNow()
    client.get("http://www.gstatic.com/generate_204") {
        timeout { requestTimeoutMillis = 5_000 }
    }
    start.elapsedNow().inWholeMilliseconds
}
```

### 平台引擎配置

```kotlin
// core/network/src/commonMain/kotlin/
expect fun createHttpEngine(): HttpClientEngine

// core/network/src/androidMain/kotlin/
actual fun createHttpEngine(): HttpClientEngine = OkHttp.create {
    config {
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(15, TimeUnit.SECONDS)
    }
}

// core/network/src/iosMain/kotlin/
actual fun createHttpEngine(): HttpClientEngine = Darwin.create {
    configureSession {
        setAllowsCellularAccess(true)
        setTimeoutIntervalForRequest(15.0)
    }
}
```

---

## 4. Room → Room KMP

### 变更点

Room 2.7+ 已原生支持 KMP，主要变更在构建配置和驱动层：

```kotlin
// build.gradle.kts (core/database)
plugins {
    id("kmp-library")
    id("androidx.room")
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled) // iOS 使用 bundled SQLite
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

### Entity/DAO 保持不变

```kotlin
// 现有代码几乎不需要改动，只需移到 commonMain
@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "protocol") val protocol: String,
    @ColumnInfo(name = "link") val link: String,
    // ...
)

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY name ASC")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: NodeEntity): Long

    @Delete
    suspend fun delete(node: NodeEntity)
}
```

### 平台数据库实例化

```kotlin
// commonMain
expect fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<XrayDatabase>

// androidMain
actual fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<XrayDatabase> {
    val appContext = context.androidContext.applicationContext
    val dbFile = appContext.getDatabasePath("xrayfa.db")
    return Room.databaseBuilder<XrayDatabase>(appContext, dbFile.absolutePath)
}

// iosMain
actual fun getDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<XrayDatabase> {
    val dbFilePath = "${NSHomeDirectory()}/Documents/xrayfa.db"
    return Room.databaseBuilder<XrayDatabase>(name = dbFilePath)
        .setDriver(BundledSQLiteDriver())  // iOS 使用 bundled driver
}
```

### Migration 处理

- Android: 保留所有现有 migration (v1→v2→v3→v4→v5)，确保用户升级无损
- iOS: 新安装直接创建 v5 schema，无需 migration

---

## 5. DataStore → DataStore KMP

### 几乎无代码变更

DataStore 从 1.1.0 开始支持 KMP，API 完全相同：

```kotlin
// commonMain (与现有代码基本一致)
class PreferencesDataSource(private val dataStore: DataStore<Preferences>) {
    companion object {
        val SOCKS_PORT_KEY = intPreferencesKey("socks_port")
        val HTTP_PORT_KEY = intPreferencesKey("http_port")
        val SELECTED_NODE_KEY = longPreferencesKey("selected_node_id")
        val AUTO_CONNECT_KEY = booleanPreferencesKey("auto_connect")
        val DNS_STRATEGY_KEY = stringPreferencesKey("dns_strategy")
    }

    val socksPort: Flow<Int> = dataStore.data.map { it[SOCKS_PORT_KEY] ?: 10808 }
    val httpPort: Flow<Int> = dataStore.data.map { it[HTTP_PORT_KEY] ?: 10809 }

    suspend fun setSocksPort(port: Int) {
        dataStore.edit { it[SOCKS_PORT_KEY] = port }
    }
}
```

### 平台工厂

```kotlin
// androidMain
actual fun createDataStore(context: PlatformContext): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create {
        context.androidContext.preferencesDataStoreFile("xrayfa_prefs")
    }
}

// iosMain
actual fun createDataStore(context: PlatformContext): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create {
        // App Group 路径 (支持与 Network Extension 共享)
        val dir = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier("group.com.xrayfa")!!
            .path!!
        Path("$dir/xrayfa_prefs.preferences_pb")
    }
}
```

---

## 6. Navigation3 → Decompose

### 概念对照

| Navigation3 | Decompose |
|-------------|-----------|
| `NavHost` | `Children` / `ChildStack` |
| Destination 类 | `Config` sealed interface |
| `navigate()` | `navigation.push()` / `pop()` |
| `BackStackEntry` | `Child.Instance` |
| ViewModel scoping | ComponentContext lifecycle |
| SavedStateHandle | Decompose state keeper |

### 现有导航结构迁移

```kotlin
// 现有 Navigation3 (简化)
// ui/navigation/NavigateDestination.kt
sealed class NavigateDestination {
    object Home : NavigateDestination()
    object NodeList : NavigateDestination()
    data class Edit(val nodeId: Long) : NavigateDestination()
    object Settings : NavigateDestination()
    object Subscription : NavigateDestination()
    object Logcat : NavigateDestination()
    object ScanQR : NavigateDestination()
    object Apps : NavigateDestination()
    object RouteSettings : NavigateDestination()
    object Config : NavigateDestination()
}

// 迁移后 Decompose
@Serializable
sealed interface AppConfig {
    @Serializable data object Home : AppConfig
    @Serializable data object NodeList : AppConfig
    @Serializable data class Edit(val nodeId: Long) : AppConfig
    @Serializable data object Settings : AppConfig
    @Serializable data object Subscription : AppConfig
    @Serializable data object Logcat : AppConfig
    @Serializable data object ScanQR : AppConfig
    @Serializable data object Apps : AppConfig
    @Serializable data object RouteSettings : AppConfig
    @Serializable data object Config : AppConfig
}

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<AppConfig>()

    override val childStack: Value<ChildStack<AppConfig, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = AppConfig.serializer(),
            initialConfiguration = AppConfig.Home,
            handleBackButton = true,
            childFactory = ::createChild,
        )

    override fun navigateTo(destination: AppConfig) {
        navigation.push(destination)
    }

    override fun onBack() {
        navigation.pop()
    }
}
```

### Compose 集成

```kotlin
@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    val childStack by component.childStack.subscribeAsState()

    Children(
        stack = childStack,
        modifier = modifier,
        animation = stackAnimation(fade() + scale()),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Home -> HomeScreen(instance.component)
            is RootComponent.Child.NodeList -> NodeListScreen(instance.component)
            is RootComponent.Child.Settings -> SettingsScreen(instance.component)
            is RootComponent.Child.Logcat -> LogcatScreen(instance.component)
            is RootComponent.Child.ScanQR -> ScanQRScreen(instance.component)
            // ...
        }
    }
}
```

---

## 7. AndroidX ViewModel → Decompose Component

### 对照

| ViewModel | Decompose Component |
|-----------|-------------------|
| `class XViewModel : ViewModel()` | `class XComponent(ctx: ComponentContext)` |
| `viewModelScope` | `coroutineScope()` (from componentContext) |
| `SavedStateHandle` | `stateKeeper` |
| `LiveData` / `StateFlow` | `Value<T>` / `MutableValue<T>` |
| `ViewModelProvider.Factory` | 直接构造 (DI by Koin) |

### 迁移示例

```kotlin
// 现有 ViewModel (简化)
class XrayViewModel @Inject constructor(
    private val nodeRepo: NodeRepository,
    private val subRepo: SubscriptionRepository,
) : ViewModel() {
    private val _nodes = MutableStateFlow<List<Node>>(emptyList())
    val nodes: StateFlow<List<Node>> = _nodes.asStateFlow()

    init {
        viewModelScope.launch {
            nodeRepo.getAllNodes().collect { _nodes.value = it }
        }
    }

    fun deleteNode(id: Long) {
        viewModelScope.launch { nodeRepo.deleteNode(id) }
    }
}

// 迁移后 Decompose Component
class DefaultNodeListComponent(
    componentContext: ComponentContext,
    private val nodeRepo: NodeRepository,
) : NodeListComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableValue(NodeListState())
    override val state: Value<NodeListState> = _state

    init {
        scope.launch {
            nodeRepo.getAllNodes().collect { nodes ->
                _state.update { it.copy(nodes = nodes) }
            }
        }
    }

    override fun deleteNode(id: Long) {
        scope.launch { nodeRepo.deleteNode(id) }
    }
}

data class NodeListState(
    val nodes: List<Node> = emptyList(),
    val isLoading: Boolean = false,
)
```

---

## 8. 版本目录完整更新

```toml
# gradle/libs.versions.toml (最终状态)

[versions]
agp = "8.10.0"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
compose-multiplatform = "1.7.0"
compose-bom = "2026.03.00"

# KMP 核心
kotlinx-coroutines = "1.9.0"
kotlinx-serialization = "1.7.3"
kotlinx-datetime = "0.6.2"

# 数据层
room = "2.7.0"
sqlite-bundled = "2.5.0"
datastore = "1.1.7"

# 网络
ktor = "3.1.0"

# DI — 当前 4.0.1；目标 4.1.1+（见 Step 21 版本决策）
koin = "4.0.1"

# 导航/架构
decompose = "3.2.0"

# 日志
kermit = "2.0.5"

# 相机 (Android only)
cameraCore = "1.7.0-alpha01"
zxing = "3.5.4"

# 其他
maxmind-geoip2 = "4.2.0"

[libraries]
# KMP 核心
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

# Room KMP
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite-bundled" }

# DataStore KMP
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }

# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }

# Decompose
decompose = { module = "com.arkivanov.decompose:decompose", version.ref = "decompose" }
decompose-compose = { module = "com.arkivanov.decompose:extensions-compose", version.ref = "decompose" }

# Logging
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
room = { id = "androidx.room", version.ref = "room" }
```

---

## 总结

迁移优先级排序（按阻塞关系）:

1. **kotlinx.serialization** (Phase 1) — 所有共享模块的基础
2. **模型类提取** (Phase 1) — 被其他所有模块依赖
3. **Room KMP** (Phase 2) — 数据层核心
4. **Koin** (Phase 2) — 贯穿所有模块
5. **Ktor** (Phase 2) — 网络层
6. **DataStore KMP** (Phase 2) — 设置存储
7. **Decompose** (Phase 4) — UI 层基础
8. **Compose Multiplatform** (Phase 4) — 最终 UI 共享
