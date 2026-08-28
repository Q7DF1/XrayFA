package com.android.xrayfa.agent.appfunctions

import com.android.xrayfa.agent.AgentNodeFilter
import com.android.xrayfa.agent.AgentNodeFilterKind
import com.android.xrayfa.agent.AgentScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentFunctionGateTest {

    @Test
    fun requireEnabled_true_doesNotThrow() {
        AgentFunctionGate.requireEnabled(true)
    }

    @Test
    fun requireEnabled_false_throwsDisabled() {
        val error = assertThrows(AgentDisabledException::class.java) {
            AgentFunctionGate.requireEnabled(false)
        }
        assertEquals(AgentFunctionGate.DISABLED_CODE, error.code)
    }

    @Test
    fun parseNodeFilter_knownKinds() {
        assertEquals(AgentNodeFilterKind.All, AgentFunctionGate.parseNodeFilter("All", 0).kind)
        assertEquals(AgentNodeFilterKind.Favorites, AgentFunctionGate.parseNodeFilter("favorites", 0).kind)
        assertEquals(AgentNodeFilterKind.Manual, AgentFunctionGate.parseNodeFilter("MANUAL", 0).kind)
        val bySub = AgentFunctionGate.parseNodeFilter("SubscriptionId", 7)
        assertEquals(AgentNodeFilterKind.SubscriptionId, bySub.kind)
        assertEquals(7, bySub.subscriptionId)
    }

    @Test
    fun parseNodeFilter_unknown_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentFunctionGate.parseNodeFilter("secret", 0)
        }
    }

    @Test
    fun parseScreen_knownTargets() {
        assertEquals(AgentScreen.Home, AgentFunctionGate.parseScreen("Home"))
        assertEquals(AgentScreen.Config, AgentFunctionGate.parseScreen("config"))
        assertEquals(AgentScreen.Settings, AgentFunctionGate.parseScreen("SETTINGS"))
        assertEquals(AgentScreen.Subscriptions, AgentFunctionGate.parseScreen("subscriptions"))
        assertEquals(AgentScreen.Apps, AgentFunctionGate.parseScreen("apps"))
        assertEquals(AgentScreen.RouteSettings, AgentFunctionGate.parseScreen("route_settings"))
    }

    @Test
    fun parseScreen_unknown_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentFunctionGate.parseScreen("secret")
        }
    }
}
