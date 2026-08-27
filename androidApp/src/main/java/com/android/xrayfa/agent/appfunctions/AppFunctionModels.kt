package com.android.xrayfa.agent.appfunctions

import androidx.appfunctions.AppFunctionSerializable
import com.android.xrayfa.agent.AgentActionResult
import com.android.xrayfa.agent.AgentAppInfo
import com.android.xrayfa.agent.AgentDelayResult
import com.android.xrayfa.agent.AgentErrorCode
import com.android.xrayfa.agent.AgentNodeSummary
import com.android.xrayfa.agent.AgentSettingsSummary
import com.android.xrayfa.agent.AgentSubscriptionSummary
import com.android.xrayfa.agent.AgentTrafficSpeeds
import com.android.xrayfa.agent.AgentVpnStatus

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnVpnStatus(
    /** Whether the VPN tunnel is currently connected. */
    val connected: Boolean,
    /** Most recent connect error, if any. Never includes node credentials. */
    val lastError: String?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnNodeSummary(
    /** Local node id. */
    val id: Int,
    /** Display remark. */
    val remark: String?,
    /** Protocol name without share-link prefix, e.g. vless. */
    val protocol: String,
    /** Server hostname or IP. */
    val address: String,
    /** Server port. */
    val port: Int,
    /** Whether this node is currently selected. */
    val selected: Boolean,
    /** Whether this node is favorited. */
    val favorite: Boolean,
    /** Owning subscription id; -1 for manual nodes. */
    val subscriptionId: Int,
    /** ISO country code when known. */
    val countryIso: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnSubscriptionSummary(
    /** Local subscription id. */
    val id: Int,
    /** User-visible mark. Does not include the subscription URL. */
    val mark: String,
    /** Whether auto-update is enabled. */
    val autoUpdate: Boolean,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnSettingsSummary(
    /** Theme code: 0 light, 1 dark, 2 auto. */
    val darkMode: Int,
    /** Routing mode name: GLOBAL or ROUTE. */
    val routingMode: String,
    /** Local SOCKS port. */
    val socksPort: Int,
    /** IPv4 DNS list. */
    val dnsIpv4: String,
    /** Whether IPv6 is enabled. */
    val ipv6Enabled: Boolean,
    /** Whether the user enabled Agent functions in Settings. */
    val agentFunctionsEnabled: Boolean,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnTrafficSpeeds(
    /** Upload speed in KB/s. */
    val uploadKbps: Double,
    /** Download speed in KB/s. */
    val downloadKbps: Double,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnAppInfo(
    /** Application version name. */
    val versionName: String,
    /** Application version code. */
    val versionCode: Int,
)

internal fun AgentVpnStatus.toAppFn() = AppFnVpnStatus(
    connected = connected,
    lastError = lastError,
)

internal fun AgentNodeSummary.toAppFn() = AppFnNodeSummary(
    id = id,
    remark = remark,
    protocol = protocol,
    address = address,
    port = port,
    selected = selected,
    favorite = favorite,
    subscriptionId = subscriptionId,
    countryIso = countryIso,
)

internal fun AgentSubscriptionSummary.toAppFn() = AppFnSubscriptionSummary(
    id = id,
    mark = mark,
    autoUpdate = autoUpdate,
)

internal fun AgentSettingsSummary.toAppFn() = AppFnSettingsSummary(
    darkMode = darkMode,
    routingMode = routingMode,
    socksPort = socksPort,
    dnsIpv4 = dnsIpv4,
    ipv6Enabled = ipv6Enabled,
    agentFunctionsEnabled = agentFunctionsEnabled,
)

internal fun AgentTrafficSpeeds.toAppFn() = AppFnTrafficSpeeds(
    uploadKbps = uploadKbps,
    downloadKbps = downloadKbps,
)

internal fun AgentAppInfo.toAppFn() = AppFnAppInfo(
    versionName = versionName,
    versionCode = versionCode,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnActionResult(
    /** success, failure, or needs_consent. */
    val status: String,
    /** Agent error code when [status] is failure. */
    val code: String?,
    /** Human-readable detail. Never includes secrets. */
    val message: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppFnDelayResult(
    /** Local node id that was measured. */
    val nodeId: Int,
    /** Round-trip delay in milliseconds; null when measurement failed. */
    val delayMs: Long?,
    /** Agent error code when measurement did not succeed. */
    val error: String?,
)

internal fun AgentActionResult.toAppFn(): AppFnActionResult = when (this) {
    is AgentActionResult.Success -> AppFnActionResult(
        status = "success",
        code = null,
        message = message,
    )
    is AgentActionResult.Failure -> AppFnActionResult(
        status = "failure",
        code = code.name,
        message = message,
    )
    is AgentActionResult.NeedsUserConsent -> AppFnActionResult(
        status = "needs_consent",
        code = AgentErrorCode.VPN_NOT_PREPARED.name,
        message = reason,
    )
}

internal fun AgentDelayResult.toAppFn() = AppFnDelayResult(
    nodeId = nodeId,
    delayMs = delayMs,
    error = error?.name,
)

