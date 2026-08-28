package com.android.xrayfa.agent.appfunctions

import com.android.xrayfa.agent.AgentNodeFilter
import com.android.xrayfa.agent.AgentScreen

/** Runtime checks shared by AppFunction methods. OS-level disable is B2. */
object AgentFunctionGate {
    const val DISABLED_CODE = "AGENT_DISABLED"

    fun requireEnabled(enabled: Boolean) {
        if (!enabled) {
            throw AgentDisabledException()
        }
    }

    fun parseNodeFilter(kind: String, subscriptionId: Int): AgentNodeFilter {
        return when (kind.trim().lowercase()) {
            "all" -> AgentNodeFilter.All
            "favorites" -> AgentNodeFilter.Favorites
            "manual" -> AgentNodeFilter.Manual
            "subscriptionid", "subscription_id" -> AgentNodeFilter.subscription(subscriptionId)
            else -> throw IllegalArgumentException("Unknown filterKind: $kind")
        }
    }

    fun parseScreen(target: String): AgentScreen {
        return when (target.trim().lowercase()) {
            "home" -> AgentScreen.Home
            "config" -> AgentScreen.Config
            "settings" -> AgentScreen.Settings
            "subscriptions", "subscription" -> AgentScreen.Subscriptions
            "apps" -> AgentScreen.Apps
            "routesettings", "route_settings", "route" -> AgentScreen.RouteSettings
            else -> throw IllegalArgumentException("Unknown screen: $target")
        }
    }
}

class AgentDisabledException : IllegalStateException(AgentFunctionGate.DISABLED_CODE) {
    val code: String = AgentFunctionGate.DISABLED_CODE
}
