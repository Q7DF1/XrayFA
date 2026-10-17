package com.android.xrayfa.datastore

import com.android.xrayfa.common.config.DnsDefaults
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

    @Test
    fun dnsOverHttpsServers_defaultToEncryptedProvidersAndUseStableDiskKey() {
        assertEquals(DnsDefaults.DEFAULT_DOH_SERVERS, SettingsState().dnsOverHttpsServers)
        assertEquals("dns_over_https_servers", SettingsKeys.DNS_OVER_HTTPS_SERVERS.name)
    }
}
