package com.android.xrayfa.common.core

import kotlin.test.*

class VpnStartupFailureTest {
    @Test fun classifiesAllStartupCategories() {
        val examples = listOf(
            Triple(StartupFailureKind.NO_NODE, StartupStage.PREPARATION, "NO_NODE"),
            Triple(StartupFailureKind.INVALID_LINK, StartupStage.CONFIGURATION, "Share link server is missing"),
            Triple(StartupFailureKind.INVALID_JSON, StartupStage.CONFIGURATION, "failed to parse json config"),
            Triple(StartupFailureKind.UNSUPPORTED_FEATURE, StartupStage.CORE, "unsupported transport: quic"),
            Triple(StartupFailureKind.INVALID_PROTOCOL, StartupStage.CORE, "invalid port"),
            Triple(StartupFailureKind.INVALID_SECURITY, StartupStage.CORE, "REALITY public key is required"),
            Triple(StartupFailureKind.GEODATA_MISSING, StartupStage.CORE, "geosite.dat: no such file"),
            Triple(StartupFailureKind.GEODATA_INVALID, StartupStage.CORE, "geosite.dat > EOF"),
            Triple(StartupFailureKind.INVALID_ROUTING, StartupStage.CORE, "failed to build routing configuration > invalid field rule"),
            Triple(StartupFailureKind.INVALID_DNS, StartupStage.CORE, "failed to build DNS server"),
            Triple(StartupFailureKind.PORT_IN_USE, StartupStage.CORE, "listen tcp: address already in use"),
            Triple(StartupFailureKind.VPN_PERMISSION, StartupStage.VPN, "VPN_PERMISSION: authorization required"),
            Triple(StartupFailureKind.VPN_INTERFACE, StartupStage.VPN, "VPN_INTERFACE: establish returned null"),
            Triple(StartupFailureKind.TUN_START, StartupStage.TUN, "native traffic forwarding could not start"),
            Triple(StartupFailureKind.FILE_ACCESS, StartupStage.GEODATA, "geosite.dat: permission denied"),
            Triple(StartupFailureKind.CORE_INITIALIZATION, StartupStage.CORE, "Core did not start"),
            Triple(StartupFailureKind.UNKNOWN, StartupStage.CONFIGURATION, "Unexpected result"),
            Triple(StartupFailureKind.NATIVE_LIBRARY_MISSING, StartupStage.APPLICATION, "dlopen failed: library libgojni.so not found"),
            Triple(StartupFailureKind.NATIVE_LIBRARY_INCOMPATIBLE, StartupStage.APPLICATION, "dlopen failed: cannot locate symbol"),
        )
        examples.forEach { (expected, stage, message) ->
            assertEquals(expected, classifyStartupFailure(IllegalStateException(message), stage).kind, message)
        }
        val category = classifyStartupFailure(IllegalStateException("failed to check code CUSTOM from geosite.dat > EOF"),
            StartupStage.CORE, GeodataStatus.CATEGORY_MISSING)
        assertEquals(StartupFailureKind.GEO_CATEGORY_MISSING, category.kind)
        assertEquals("geosite.dat: CUSTOM", category.subject)
        assertEquals(20, StartupFailureKind.entries.size)
        assertEquals(20, StartupFailureKind.entries.map { it.code }.distinct().size)
    }

    @Test fun eofDoesNotAlwaysMeanCorruption() {
        val error = IllegalStateException("invalid field rule > failed to check code TELEGRAM from geosite.dat > EOF")
        assertEquals(StartupFailureKind.GEODATA_MISSING, classifyStartupFailure(error, StartupStage.CORE, GeodataStatus.MISSING).kind)
        assertEquals(StartupFailureKind.GEODATA_INVALID, classifyStartupFailure(error, StartupStage.CORE, GeodataStatus.INVALID).kind)
        assertEquals(StartupFailureKind.GEO_CATEGORY_MISSING, classifyStartupFailure(error, StartupStage.CORE, GeodataStatus.CATEGORY_MISSING).kind)
        assertEquals(StartupFailureKind.UNKNOWN, classifyStartupFailure(error, StartupStage.CORE, GeodataStatus.VALID).kind)
    }

    @Test fun prioritizesRootCauseOverRoutingWrapper() {
        val error = IllegalStateException("failed to build routing configuration", IllegalArgumentException("geosite.dat > EOF"))
        val failure = classifyStartupFailure(error, StartupStage.CORE)
        assertEquals(StartupFailureKind.GEODATA_INVALID, failure.kind)
        assertEquals(StartupStage.GEODATA, failure.stage)
        assertTrue(failure.technicalDetails.contains("Caused by"))
        assertTrue(failure.canRestoreGeodata)
    }

    @Test fun diagnosticsRedactCredentialsAndFullLinks() {
        val raw = "vless://usersecret@example.test:443?pbk=keysecret#node\n" +
            "uuid=123e4567-e89b-12d3-a456-426614174000 password=passsecret token='tokensecret' " +
            "{\"id\":\"idsecret\",\"privateKey\":\"privatesecret\",\"encryption\":\"encsecret\"}"
        val failure = classifyStartupFailure(IllegalArgumentException(raw), StartupStage.CONFIGURATION)
        listOf("usersecret", "example.test", "keysecret", "123e4567", "passsecret", "tokensecret", "idsecret", "privatesecret", "encsecret")
            .forEach { assertFalse(failure.technicalDetails.contains(it), it) }
        assertTrue(failure.technicalDetails.contains("REDACTED"))
        assertEquals(4000, redactStartupDetails("x".repeat(5000)).length)
        assertFalse(redactStartupDetails("a\u0000b").contains('\u0000'))
    }

    @Test fun failureSurvivesRetryAndClearsOnlyAfterSuccess() {
        val store = VpnStartupStateStore()
        val first = store.begin()
        val failure = classifyStartupFailure(IllegalStateException("geosite.dat > EOF"), StartupStage.CORE)
        store.fail(failure, first)
        val retry = store.begin()
        assertTrue(store.state.value.pending)
        assertEquals(failure, store.state.value.failure)
        store.succeed(first)
        assertEquals(failure, store.state.value.failure)
        store.succeed(retry)
        assertFalse(store.state.value.pending)
        assertNull(store.state.value.failure)
        store.fail(failure, first)
        assertNull(store.state.value.failure)
    }

    @Test fun lateSuccessCannotEraseSameAttemptFailure() {
        val store = VpnStartupStateStore()
        val attempt = store.begin()
        val failure = classifyStartupFailure(IllegalStateException("TUN_START"), StartupStage.TUN)
        store.fail(failure, attempt)
        store.succeed(attempt)
        assertEquals(failure, store.state.value.failure)
        assertFalse(store.state.value.pending)
    }
}
