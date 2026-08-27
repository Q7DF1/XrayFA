# Android Agent 可控能力计划（AppFunctions）

> **范围**：仅 **Android**；**iOS 暂不实现**。  
> **目标**：让系统 Agent / 助手（Gemini、MCP 类 caller）能在用户授权下，以结构化方式调用 XrayFA 的**只读查询**与**有限写操作**，而不是复刻整套 UI。  
> **实现载体**：Android Jetpack **[AppFunctions](https://developer.android.com/ai/appfunctions)**（`androidx.appfunctions`，钉 **1.0.0-alpha08**：alpha09+ 需要 compileSdk 37 + AGP 9.1，与当前 AGP 8.10 / SDK 36 不兼容）。  
> **状态**：A1–A5、B1–B2 已落地。交接：`docs/KMP_MIGRATION_STEP98_HANDOVER.md`。C1 Gemini EAP 可选、未做。

---

## 1. 背景与原则

### 1.1 为什么用 AppFunctions

| 方式 | 问题 |
|------|------|
| 导出 `MainActivity` + Intent extra（现有 shortcut） | 无 schema、无类型、无统一发现；任意 app 可发 Intent |
| 自建 ContentProvider / AIDL | 需自维护权限与文档；与系统 Agent 生态不互通 |
| **AppFunctions** | 平台级发现/执行；caller 需 `EXECUTE_APP_FUNCTIONS`；KSP 生成 metadata；与 Gemini / 设备 AI 对齐 |

### 1.2 设计原则

1. **Domain 先行**：Agent 可调用的语义在 **KMP domain/common 层**定义接口与 DTO；Android AppFunction 只做薄适配。
2. **最小暴露面**：先 **读多写少**；高危写操作必须 **显式用户确认**（VPN 授权、确认对话框、或设置开关）。
3. **复用现有逻辑**：优先委托 `VpnController`、`NodeRepository`、`SubscriptionRepository`、`SettingsRepository`、已有 Decompose 组件校验路径；**不**在 AppFunction 里复制业务规则。
4. **Android 参照不变**：接口命名与行为以 Android 现网为准；iOS 将来若要支持，再增加 actual，**不在本计划内**。
5. **可关闭**：用户可在应用内总开关关闭全部 Agent 函数；`AppFunctionManager.setAppFunctionEnabled` 与 DataStore 联动。

### 1.3 平台要求

| 项 | 值 |
|----|-----|
| compileSdk | 36（已满足） |
| AppFunctions 运行时 | API **36+** 且设备支持 extension；低版本 `AppFunctionManager.getInstance()` 为 null — **优雅降级** |
| 依赖 | `androidx.appfunctions:appfunctions` + `appfunctions-compiler`（KSP） |
| 测试 | `adb shell cmd app_function list/execute`（API 36+ 真机/模拟器） |

---

## 2. 架构

```
┌─────────────────────────────────────────────────────────────┐
│  Caller（Gemini / 系统 Agent / 调试 adb）                    │
│  EXECUTE_APP_FUNCTIONS permission                           │
└───────────────────────────┬─────────────────────────────────┘
                            │ AppFunctionManager.search / execute
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  androidApp: XrayFAAppFunctions                              │
│  @AppFunction suspend fun …(AppFunctionContext, …)          │
│  PlatformAppFunctionService（库 manifest 合并）               │
└───────────────────────────┬─────────────────────────────────┘
                            │ withContext(IO) + 权限/开关检查
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  androidApp: DefaultXrayAgentFacade                         │
│  implements XrayAgentFacade（:domain）                       │
│  节点/订阅查询委托 XrayAgentCatalog                          │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
 VpnController      NodeRepository      SettingsRepository
 SubscriptionRepository          VpnStartOptionsResolver …
```

**建议模块落点**：

| 层 | 模块 | 内容 |
|----|------|------|
| 契约 + DTO | `:domain` | `XrayAgentFacade`、`Agent*Result`、错误码、`XrayAgentCatalog`；**无** Android 依赖 |
| 实现 | `:androidApp` | `DefaultXrayAgentFacade`、VPN consent、`VpnService.prepare` |
| 暴露 | `:androidApp` | `XrayFAAppFunctions`、KSP `assets/app_functions_v2.xml`、`PlatformAppFunctionService` |
| 设置 | `:core:datastore` | `agentFunctionsEnabled: Boolean`（新 key） |

iOS：**不**新增 `expect/actual`，不在 `iosApp` 注册任何 Agent 入口。

---

## 3. 能力评估（给 Agent 什么）

按 **风险** 与 **阶段** 划分。  
风险定义：**safe** = 只读/无网络副作用；**medium** = 本地写/小范围网络；**high** = VPN/路由/删除/对外暴露。

### 3.1 Phase A — 只读（建议首批上线）

| ID | 能力 | 现有 API | 风险 | Agent 价值 |
|----|------|----------|------|------------|
| `get_vpn_status` | VPN 是否连接、最近错误 | `VpnController.state`, `connectError` | safe | 「现在连着吗？」 |
| `get_selected_node` | 当前选中节点摘要 | `NodeRepository.querySelectedNode()` | safe | 上下文 |
| `list_nodes` | 节点列表（可限字段） | `NodeRepository.allNodes` | safe | 选择前浏览 |
| `get_node` | 按 id 查节点 | `loadLinksById` | safe | 详情 |
| `list_subscriptions` | 订阅列表 | `SubscriptionRepository.allSubscriptions` | safe | 管理订阅 |
| `get_settings_summary` | 主题/路由模式/DNS/端口等摘要 | `SettingsRepository.settingsFlow` | safe | 诊断；**不**返回 SOCKS 密码明文 |
| `get_traffic_speeds` | 上下行 KB/s | `TrafficStatsSource` / Home 同源 | safe | 连接质量 |
| `get_app_version` | 版本名/版本号 | `BuildConfig` | safe | 支持 |

**刻意不暴露（Phase A）**：完整节点 URL/JSON、SOCKS 密码、完整 routing rules JSON、clipboard、logcat 全文。

### 3.2 Phase B — 安全写（需 Agent 总开关 ON）

| ID | 能力 | 现有 API | 风险 | 约束 |
|----|------|----------|------|------|
| `select_node` | 选中节点 | `updateSelectById` + `restartIfNeeded` | high | 需已选节点存在；若 VPN 已连接则重启 |
| `toggle_favorite` | 收藏/取消 | `updateFavoriteById` | safe | — |
| `connect_vpn` | 连接 | `VpnController.connect()` | high | **必须**先 `VpnService.prepare`；无选中节点则失败 |
| `disconnect_vpn` | 断开 | `VpnController.disconnect()` | high | 无额外确认（与 QS tile 一致） |
| `refresh_subscription` | 拉取订阅节点 | `fetchAndSaveNodes` | high | 需网络；尊重 `sendHwid` 设置 |
| `measure_node_delay` | 单节点延迟 | `XrayCore.measureOutboundDelay` | medium | 限流；默认 URL 来自 settings |
| `navigate_to` | 打开某 Tab/屏 | `MainActivity` + 内部 route | safe | 仅 in-app 深链，不导出敏感 extra |

### 3.3 Phase C — 高危 / 暂缓或强确认

| ID | 能力 | 风险 | 建议 |
|----|------|------|------|
| `add_node_from_url` | 从 URL 导入 | medium | 需用户确认对话框展示 URL 摘要 |
| `delete_node` | 删节点 | high | 禁止 Agent 自动调用；仅用户明确指令 + 确认 |
| `delete_subscription` | 删订阅及节点 | high | 同上 |
| `update_routing_rules` | 改路由 JSON | high | 仅高级设置已解锁 + 确认 |
| `set_lan_proxy` / `set_allowed_packages` | LAN / 分应用 | high | **不**给 Agent；仅 UI |
| `set_boot_auto_start` | 开机连 | high | **不**给 Agent |
| `import_clipboard` | 读剪贴板 | medium | **不**给 Agent（隐私） |
| `start_logcat` / `export_logs` | 日志 | medium | **不**给 Agent（PII） |

---

## 4. Domain 接口（Agent 契约）

以下为 **Agent 语义层**接口，与 AppFunction 一一对应；放在 `:domain`（或 `:common`）以便单测与文档生成。

```kotlin
/** Agent 可调用的应用能力门面。Android [DefaultXrayAgentFacade] 实现；iOS 无实现。 */
interface XrayAgentFacade {

    // ── Phase A: 只读 ──
    suspend fun getVpnStatus(): AgentVpnStatus
    suspend fun getSelectedNode(): AgentNodeSummary?
    suspend fun listNodes(filter: AgentNodeFilter = AgentNodeFilter.All): List<AgentNodeSummary>
    suspend fun getNode(nodeId: Int): AgentNodeSummary?
    suspend fun listSubscriptions(): List<AgentSubscriptionSummary>
    suspend fun getSettingsSummary(): AgentSettingsSummary
    suspend fun getTrafficSpeeds(): AgentTrafficSpeeds
    suspend fun getAppInfo(): AgentAppInfo

    // ── Phase B: 写 ──
    suspend fun selectNode(nodeId: Int): AgentActionResult
    suspend fun setFavorite(nodeId: Int, favorite: Boolean): AgentActionResult
    suspend fun connectVpn(): AgentActionResult          // 内含 prepare 流程
    suspend fun disconnectVpn(): AgentActionResult
    suspend fun refreshSubscription(subscriptionId: Int): AgentActionResult
    suspend fun measureNodeDelay(nodeId: Int, url: String? = null): AgentDelayResult
    suspend fun openScreen(target: AgentScreen): AgentActionResult
}
```

### 4.1 DTO（AppFunction 可序列化）

所有返回类型需 `@AppFunctionSerializable`（Android 侧注解）；domain 层用 **纯 data class**，androidApp 模块做 typealias 或 mirror class（若 compiler 要求注解在 android 模块）。

```kotlin
/** 节点摘要 — 不含完整 share link / jsonData */
data class AgentNodeSummary(
    val id: Int,
    val remark: String?,
    val protocol: String,
    val address: String,
    val port: Int,
    val selected: Boolean,
    val favorite: Boolean,
    val subscriptionId: Int,
    val countryIso: String,
)

data class AgentVpnStatus(
    val connected: Boolean,
    val lastError: String?,
)

data class AgentSubscriptionSummary(
    val id: Int,
    val mark: String,
    val autoUpdate: Boolean,
    // 不含 url
)

data class AgentTrafficSpeeds(
    val uploadKbps: Double,
    val downloadKbps: Double,
)

data class AgentAppInfo(
    val versionName: String,
    val versionCode: Int,
)

data class AgentDelayResult(
    val nodeId: Int,
    val delayMs: Long?,
    val error: AgentErrorCode? = null,
)

data class AgentSettingsSummary(
    val darkMode: Int,
    val routingMode: String,
    val socksPort: Int,
    val dnsIpv4: String,
    val ipv6Enabled: Boolean,
    val agentFunctionsEnabled: Boolean,
    // 不含 socksPassword
)

sealed class AgentActionResult {
    data class Success(val message: String = "ok") : AgentActionResult()
    data class Failure(val code: AgentErrorCode, val message: String) : AgentActionResult()
    data class NeedsUserConsent(val reason: String) : AgentActionResult()
}

enum class AgentErrorCode {
    AGENT_DISABLED,
    VPN_NOT_PREPARED,
    NO_SELECTED_NODE,
    NODE_NOT_FOUND,
    SUBSCRIPTION_NOT_FOUND,
    NETWORK_ERROR,
    VPN_CONNECT_FAILED,
    RATE_LIMITED,
    UNSUPPORTED,
}

enum class AgentScreen { Home, Config, Settings, Subscriptions, Apps, RouteSettings }

data class AgentNodeFilter(
    val kind: AgentNodeFilterKind = AgentNodeFilterKind.All,
    val subscriptionId: Int = 0,
)
// Manual 节点 subscriptionId == -1（与 ConfigFilterIds.SUB_MANUAL 对齐）
```

### 4.2 与现有 Repository 映射

| Facade 方法 | 委托 |
|-------------|------|
| `getVpnStatus` | `vpnController.state.value`, `connectError.value` |
| `listNodes` | `XrayAgentCatalog` → `nodeRepository.allNodes.first()` + filter |
| `selectNode` | Catalog: `clearSelection` + `updateSelectById`；Android Facade 再 `vpnController.restartIfNeeded()` |
| `connectVpn` | `VpnConnectCoordinator.prepareConfigForConnect()` + `vpnController.connect()` |
| `refreshSubscription` | `getSubscriptionById` + `fetchAndSaveNodes` |
| `measureNodeDelay` | `ParserFactory` + `XrayCore.measureOutboundDelay`（与 ViewModel 同限流） |
| `getSettingsSummary` | `settingsRepository.settingsFlow.first()` |

---

## 5. AppFunction 层（Android 实现约定）

### 5.1 依赖（`androidApp/build.gradle.kts` + catalog）

```kotlin
// gradle/libs.versions.toml
appfunctions = "1.0.0-alpha08" // 勿升 alpha09+，除非同时升 compileSdk 37 + AGP 9.1

// androidApp
implementation(libs.androidx.appfunctions)
implementation(libs.androidx.appfunctions.service)
ksp(libs.androidx.appfunctions.compiler)
ksp { arg("appfunctions:aggregateAppFunctions", "true") }
```

### 5.2 Service 骨架（alpha08）

```kotlin
class XrayFAAppFunctions(private val facade: XrayAgentFacade) {

    @AppFunction(isDescribedByKDoc = true)
    suspend fun getVpnStatus(ctx: AppFunctionContext): AppFnVpnStatus =
        withContext(Dispatchers.IO) {
            requireAgentEnabled()
            facade.getVpnStatus().toAppFn()
        }

    // Phase B（B1，STEP98）：
    // @AppFunction
    // suspend fun connectVpn(ctx: AppFunctionContext): AppFnActionResult = …
}
```

`XrayFAApplication` 实现 `AppFunctionConfiguration.Provider`，用 Koin 工厂构造 enclosing class。系统绑定库 manifest 里的 `PlatformAppFunctionService`。

**规则（alpha08 compiler 约束）**：

- 每个 `@AppFunction` **首参**必须是 `AppFunctionContext`（`androidx.appfunctions.service.AppFunction`）
- 使用 `suspend` + `Dispatchers.IO`（默认主线程）
- 参数/返回值类型需 `@AppFunctionSerializable`
- KSP 生成 `assets/app_functions_v2.xml` 与 `XrayFAAppFunctionsIds`（`ClassName#method`）

### 5.3 Function ID 命名

建议稳定前缀：`com.android.xrayfa.agent.<name>`，与 KSP 生成 `XrayFAAppFunctionServiceIds` 常量对齐。

| AppFunction 方法 | 建议 functionId | Phase |
|------------------|-----------------|-------|
| `getVpnStatus` | `…get_vpn_status` | A |
| `listNodes` | `…list_nodes` | A |
| `connectVpn` | `…connect_vpn` | B |
| … | … | … |

### 5.4 启用门控

1. **用户设置**：`SettingsRepository.agentFunctionsEnabled`（默认 **false**）
2. **运行时**：`AppFunctionManager.setAppFunctionEnabled(id, ENABLED/DISABLED)` 随设置变化
3. **单次高危操作**：`connectVpn` / `selectNode` 可弹 `Activity` 确认（`AppFunctionContext` 提供启动 Activity 能力）

---

## 6. 安全与隐私

| 威胁 | 缓解 |
|------|------|
| 未授权 caller 调用 | 系统限制 `EXECUTE_APP_FUNCTIONS`；仅特权 Agent |
| Agent 悄悄连 VPN | `VpnService.prepare` + 可选确认 UI；记录到 AppLog |
| 泄露节点 URL/密码 | DTO 摘要化；settings 不返回密码 |
| 订阅 URL 带 token | `listSubscriptions` 仅返回 mark + id，**不**返回完整 URL |
| 滥用测速 | `measureNodeDelay` 限流（如 1 req / 5s / node） |
| 用户误触 | 总开关默认关；设置页说明 |

**审计**：Agent 调用写 `Logger` + 可选 `AppLogStore` 一行（不含 secrets）。

---

## 7. 实施步骤（Android only）

| 步骤 | 内容 | 产出 |
|------|------|------|
| **A1** | 在 `:domain` 增加 `XrayAgentFacade` + DTO + `AgentErrorCode` + `XrayAgentCatalog` | ✅ 接口 + `commonTest`（内存 Fake repo） |
| **A2** | `DefaultXrayAgentFacade` in `:androidApp`；Koin `single<XrayAgentFacade>` | ✅ JVM 单测（Fake catalog / VPN，无 Robolectric） |
| **A3** | DataStore：`agent_functions_enabled`；设置页开关 + 说明文案 | ✅ 默认 false；四套 compose-resources |
| **A4** | 接入 AppFunctions 依赖 + `XrayFAAppFunctions` Phase A 只读函数 | ✅ metadata + manifest（STEP96） |
| **A5** | `adb shell cmd app_function` 验证 list/execute | ✅ 手测清单（STEP97，API 36 真机） |
| **B1** | Phase B 写操作 + VPN prepare 流程 | ✅ connect/select/refresh/delay/openScreen（STEP98） |
| **B2** | `setAppFunctionEnabled` 与设置联动 | ✅ DataStore → OS enable（STEP98） |
| **C1** | （可选）Gemini EAP / 私有预览注册 | 外部联调 |
| — | **iOS** | **明确跳过**；不在 roadmap 本阶段 |

---

## 8. 与现有代码的关系

| 现有机制 | Agent 计划 |
|----------|------------|
| `MainActivity` shortcut (`open_scan`, `start_service`) | **保留**；Agent 走 AppFunction，不扩 Intent 面 |
| `QuickStartTileService` | 不变 |
| `BootBroadcastReceiver` | 不给 Agent |
| Decompose `HomeComponent` 等 | Facade 复用同 repository，**不**直接绑 UI |
| 未来 Android → `RootContent` | 与 Agent **正交**；`openScreen` 可后发 Intent 到 MainActivity |

---

## 9. 测试计划

```bash
# API 36+ 设备（Samsung 上子命令名为 list-app-functions / execute-app-function）
adb shell cmd app_function list-app-functions --user 0
adb shell "cmd app_function execute-app-function --package com.android.xrayfa \
  --function 'com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions#getVpnStatus' \
  --parameters '{}' --brief-yaml"
# 有参：标量包成单元素数组，例如 listNodes
# --parameters '{"filterKind":["All"],"subscriptionId":[0]}'
```

- [x] Agent 关闭时 execute 返回 disabled / AGENT_DISABLED
- [x] 无 VPN 权限时 `connectVpn` → `NeedsUserConsent`（Facade 单测；真机 adb 待补）
- [x] `list_nodes` 不包含完整 url 字段
- [x] 选中不存在 nodeId → `NODE_NOT_FOUND`（Catalog / Facade 单测）
- [x] `getTrafficSpeeds` 无采样时回零，不再挂起（STEP98）
- [ ] compileSdk 35 / API &lt; 36 设备上 App 正常启动（`getInstance` 可为 null；本步无该设备）

---

## 10. 参考

- [Overview of AppFunctions](https://developer.android.com/ai/appfunctions)
- [Add AppFunctions to your app](https://developer.android.com/ai/appfunctions/add-appfunctions)
- [androidx.appfunctions releases](https://developer.android.com/jetpack/androidx/releases/appfunctions)
- 项目内：`AGENT.md`、`platform/vpn/VpnController.kt`、`core/data/RoomNodeRepository.kt`
- 现有外部入口：`MainActivity` shortcuts（`docs/ANDROID_AGENT_APPFUNCTIONS_PLAN.md` §8）

---

## 附录 A：AppFunction ↔ Facade 完整映射表（目标态）

| AppFunction | XrayAgentFacade | Phase | 默认启用 |
|-------------|-----------------|-------|----------|
| `getVpnStatus` | `getVpnStatus()` | A | 随总开关 |
| `getSelectedNode` | `getSelectedNode()` | A | 随总开关 |
| `listNodes` | `listNodes(filter)` | A | 随总开关 |
| `getNode` | `getNode(id)` | A | 随总开关 |
| `listSubscriptions` | `listSubscriptions()` | A | 随总开关 |
| `getSettingsSummary` | `getSettingsSummary()` | A | 随总开关 |
| `getTrafficSpeeds` | `getTrafficSpeeds()` | A | 随总开关 |
| `getAppInfo` | `getAppInfo()` | A | 随总开关 |
| `selectNode` | `selectNode(id)` | B | 随总开关 |
| `setFavorite` | `setFavorite(id, fav)` | B | 随总开关 |
| `connectVpn` | `connectVpn()` | B | 随总开关 |
| `disconnectVpn` | `disconnectVpn()` | B | 随总开关 |
| `refreshSubscription` | `refreshSubscription(id)` | B | 随总开关 |
| `measureNodeDelay` | `measureNodeDelay(id, url?)` | B | 随总开关 |
| `openScreen` | `openScreen(target)` | B | 随总开关 |

**不在映射表内 = 故意不对 Agent 开放**（见 §3.3）。
