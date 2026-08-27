package com.android.xrayfa.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsStateDefaultsTest {

    @Test
    fun agentFunctionsEnabled_defaultsFalse() {
        assertFalse(SettingsState().agentFunctionsEnabled)
    }

    @Test
    fun agentFunctionsEnabled_diskKeyIsStable() {
        assertEquals("agent_functions_enabled", SettingsKeys.AGENT_FUNCTIONS_ENABLED.name)
    }
}
