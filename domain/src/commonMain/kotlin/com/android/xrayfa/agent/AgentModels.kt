package com.android.xrayfa.agent

/**
 * Agent-facing DTOs. Summaries intentionally omit share links, node JSON, SOCKS
 * passwords, and subscription URLs. Android AppFunction mirrors add
 * `@AppFunctionSerializable` in `:androidApp` (A4).
 */

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

data class AgentSubscriptionSummary(
    val id: Int,
    val mark: String,
    val autoUpdate: Boolean,
)

data class AgentVpnStatus(
    val connected: Boolean,
    val lastError: String?,
)

data class AgentSettingsSummary(
    val darkMode: Int,
    val routingMode: String,
    val socksPort: Int,
    val dnsIpv4: String,
    val ipv6Enabled: Boolean,
    val agentFunctionsEnabled: Boolean,
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

enum class AgentNodeFilterKind {
    All,
    Favorites,
    Manual,
    SubscriptionId,
}

/**
 * Node list filter. [subscriptionId] is used only when [kind] is [AgentNodeFilterKind.SubscriptionId].
 *
 * Manual nodes use [MANUAL_SUBSCRIPTION_ID] (`-1`), matching Config / ViewModel.
 */
data class AgentNodeFilter(
    val kind: AgentNodeFilterKind = AgentNodeFilterKind.All,
    val subscriptionId: Int = 0,
) {
    companion object {
        const val MANUAL_SUBSCRIPTION_ID: Int = -1

        val All = AgentNodeFilter(AgentNodeFilterKind.All)
        val Favorites = AgentNodeFilter(AgentNodeFilterKind.Favorites)
        val Manual = AgentNodeFilter(AgentNodeFilterKind.Manual)

        fun subscription(id: Int) = AgentNodeFilter(AgentNodeFilterKind.SubscriptionId, id)
    }
}

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

enum class AgentScreen {
    Home,
    Config,
    Settings,
    Subscriptions,
    Apps,
    RouteSettings,
}
