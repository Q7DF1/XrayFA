package com.android.xrayfa.agent.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import com.android.xrayfa.agent.XrayAgentFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Phase A+B AppFunctions. KSP generates `assets/app_functions_v2.xml`
 * and [XrayFAAppFunctionsIds]. The system binds
 * [androidx.appfunctions.service.PlatformAppFunctionService] from the library manifest.
 *
 * Gating: user switch [com.android.xrayfa.datastore.SettingsState.agentFunctionsEnabled],
 * plus OS-level [androidx.appfunctions.AppFunctionManager.setAppFunctionEnabled] (B2).
 */
class XrayFAAppFunctions(
    private val facade: XrayAgentFacade = GlobalContext.get().get(),
) {

    /**
     * Returns whether the VPN tunnel is connected and the last connect error.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getVpnStatus(ctx: AppFunctionContext): AppFnVpnStatus = withAgentEnabled(ctx) {
        facade.getVpnStatus().toAppFn()
    }

    /**
     * Returns the currently selected node summary, or null if none is selected.
     * Does not include share links or node JSON.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getSelectedNode(ctx: AppFunctionContext): AppFnNodeSummary? = withAgentEnabled(ctx) {
        facade.getSelectedNode()?.toAppFn()
    }

    /**
     * Lists node summaries.
     *
     * @param filterKind One of All, Favorites, Manual, SubscriptionId.
     * @param subscriptionId Used when [filterKind] is SubscriptionId.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listNodes(
        ctx: AppFunctionContext,
        filterKind: String,
        subscriptionId: Int,
    ): List<AppFnNodeSummary> = withAgentEnabled(ctx) {
        val filter = try {
            AgentFunctionGate.parseNodeFilter(filterKind, subscriptionId)
        } catch (e: IllegalArgumentException) {
            throw AppFunctionInvalidArgumentException(e.message ?: "Invalid filterKind")
        }
        facade.listNodes(filter).map { it.toAppFn() }
    }

    /**
     * Returns a node summary by id, or null if it does not exist.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNode(ctx: AppFunctionContext, nodeId: Int): AppFnNodeSummary? =
        withAgentEnabled(ctx) {
            facade.getNode(nodeId)?.toAppFn()
        }

    /**
     * Lists subscriptions. Marks only — never the subscription URL.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listSubscriptions(ctx: AppFunctionContext): List<AppFnSubscriptionSummary> =
        withAgentEnabled(ctx) {
            facade.listSubscriptions().map { it.toAppFn() }
        }

    /**
     * Returns a settings summary. Does not include the SOCKS password.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getSettingsSummary(ctx: AppFunctionContext): AppFnSettingsSummary =
        withAgentEnabled(ctx) {
            facade.getSettingsSummary().toAppFn()
        }

    /**
     * Returns current upload and download speeds in KB/s.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getTrafficSpeeds(ctx: AppFunctionContext): AppFnTrafficSpeeds =
        withAgentEnabled(ctx) {
            facade.getTrafficSpeeds().toAppFn()
        }

    /**
     * Returns the app version name and version code.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getAppInfo(ctx: AppFunctionContext): AppFnAppInfo = withAgentEnabled(ctx) {
        facade.getAppInfo().toAppFn()
    }

    /**
     * Selects a node by local id. Restarts the VPN if it is already connected.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun selectNode(ctx: AppFunctionContext, nodeId: Int): AppFnActionResult =
        withAgentEnabled(ctx) { facade.selectNode(nodeId).toAppFn() }

    /**
     * Sets or clears the favorite flag for a node.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setFavorite(
        ctx: AppFunctionContext,
        nodeId: Int,
        favorite: Boolean,
    ): AppFnActionResult = withAgentEnabled(ctx) {
        facade.setFavorite(nodeId, favorite).toAppFn()
    }

    /**
     * Connects the VPN. Requests system VPN consent when it has not been granted.
     * Returns status needs_consent when the user must approve the VPN permission dialog.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun connectVpn(ctx: AppFunctionContext): AppFnActionResult =
        withAgentEnabled(ctx) { facade.connectVpn().toAppFn() }

    /**
     * Disconnects the VPN tunnel.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun disconnectVpn(ctx: AppFunctionContext): AppFnActionResult =
        withAgentEnabled(ctx) { facade.disconnectVpn().toAppFn() }

    /**
     * Refreshes nodes for a subscription. Does not expose the subscription URL.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun refreshSubscription(
        ctx: AppFunctionContext,
        subscriptionId: Int,
    ): AppFnActionResult = withAgentEnabled(ctx) {
        facade.refreshSubscription(subscriptionId).toAppFn()
    }

    /**
     * Measures outbound delay for one node. Rate-limited to once per 5 seconds per node.
     *
     * @param nodeId Local node id.
     * @param url Test URL. Empty string uses the in-app delay-test setting.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun measureNodeDelay(
        ctx: AppFunctionContext,
        nodeId: Int,
        url: String,
    ): AppFnDelayResult = withAgentEnabled(ctx) {
        facade.measureNodeDelay(nodeId, url.takeIf { it.isNotBlank() }).toAppFn()
    }

    /**
     * Opens an in-app screen.
     *
     * @param target One of Home, Config, Settings, Subscriptions, Apps, RouteSettings.
     * Apps and RouteSettings land on the Settings tab.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun openScreen(ctx: AppFunctionContext, target: String): AppFnActionResult =
        withAgentEnabled(ctx) {
            val screen = try {
                AgentFunctionGate.parseScreen(target)
            } catch (e: IllegalArgumentException) {
                throw AppFunctionInvalidArgumentException(e.message ?: "Invalid screen")
            }
            facade.openScreen(screen).toAppFn()
        }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun <T> withAgentEnabled(ctx: AppFunctionContext, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            try {
                AgentFunctionGate.requireEnabled(facade.getSettingsSummary().agentFunctionsEnabled)
            } catch (_: AgentDisabledException) {
                throw AppFunctionDeniedException(AgentFunctionGate.DISABLED_CODE)
            }
            block()
        }
}
