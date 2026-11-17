package com.android.xrayfa.cli

import kotlin.test.*
import kotlinx.serialization.json.*

class JournalAndPolicyTest {
    private val empty = JsonArray(emptyList())

    private fun fixture(phase: SessionPhase, policy: Boolean = false): JsonObject =
        buildJsonObject {
            put("schemaVersion", 1)
            put("controllerPid", 42)
            put("controllerStart", "100")
            put("controllerExe", "/usr/bin/xrayfa")
            put("nodeId", 1)
            put("uid", 1000)
            put("configSha256", "a".repeat(64))
            put("coreSha256", "b".repeat(64))
            put("ownershipToken", "c".repeat(32))
            put("bootId", "fixture-boot")
            put("namespace", "net:[123]")
            put("phase", phase.wire)
            put("corePath", "/usr/bin/xray")
            put("configPath", "/run/xrayfa/artifacts/42-123/final.json")
            put("coreDevice", "1")
            put("coreInode", "2")
            put("corePid", 43)
            put("coreStart", "101")
            putJsonObject("baseline") {
                for (key in listOf("routes", "rules", "links", "addresses")) put(key, empty)
                putJsonObject("resolver") { put("backend", "static-resolv.conf") }
            }
            put("managementRouteIntent", empty)
            put("managementRoutesAfter", empty)
            put("nftTable", "xfa_42_123")
            putJsonObject("nftIntent") {}
            putJsonObject("nftAfter") {}
            put(
                "tunIntent",
                buildJsonObject {
                    put("interface", "xrayfa0")
                    put("address", "198.18.0.1/30")
                    put("routes", JsonArray(TunPolicy.V1.routes.map(::JsonPrimitive)))
                },
            )
            put("tunAfter", link(TunPolicy.V1))
            put("routesAfter", empty)
            put("rulesAfter", empty)
            put("resolverIntent", true)
            putJsonObject("resolverAfter") {}
            put("unknownExtension", "preserve me")
            if (policy) put("tunPolicy", TunPolicy.V1.encode())
        }

    private fun link(policy: TunPolicy): JsonObject = buildJsonObject {
        put("ifindex", 5)
        put("ifname", policy.interfaceName)
        put("mtu", policy.mtu)
        putJsonArray("addr_info") {
            add(
                buildJsonObject {
                    put("local", policy.gateway)
                    put("prefixlen", policy.prefixLength)
                }
            )
        }
    }

    private fun routes(policy: TunPolicy) =
        policy.routes.map {
            buildJsonObject {
                put("dst", it)
                put("dev", policy.interfaceName)
            }
        }

    @Test
    fun historicalWireValuesAndExtensionFieldsRoundTrip() {
        for (phase in SessionPhase.entries.filter { it != SessionPhase.RESTORED }) {
            val raw = fixture(phase)
            val decoded = RecoveryJournal.decode(raw)
            assertEquals(raw, decoded.json)
            assertEquals(TunPolicy.V1, decoded.policy)
            assertEquals(ProcessIdentity(43, 101uL, 1uL, 2uL), decoded.core)
            assertEquals(
                raw,
                RecoveryJournal.decode(fixture(phase, true)).json.let {
                    JsonObject(it - "tunPolicy")
                },
            )
        }
        assertEquals("Running", SessionPhase.RUNNING.wire)
        assertEquals("tun-intent", SessionPhase.TUN_INTENT.wire)
    }

    private val transitions =
        listOf(
            SessionPhase.LEASED to SessionPhase.MANAGEMENT_ROUTE_INTENT,
            SessionPhase.LEASED to SessionPhase.IPV6_INTENT,
            SessionPhase.MANAGEMENT_ROUTE_INTENT to SessionPhase.MANAGEMENT_ROUTES_APPLIED,
            SessionPhase.MANAGEMENT_ROUTES_APPLIED to SessionPhase.IPV6_INTENT,
            SessionPhase.IPV6_INTENT to SessionPhase.IPV6_APPLIED,
            SessionPhase.IPV6_APPLIED to SessionPhase.CORE_INTENT,
            SessionPhase.CORE_INTENT to SessionPhase.CORE_GATED,
            SessionPhase.CORE_GATED to SessionPhase.TUN_INTENT,
            SessionPhase.CORE_GATED to SessionPhase.TUN_READY,
            SessionPhase.TUN_INTENT to SessionPhase.TUN_READY,
            SessionPhase.TUN_READY to SessionPhase.RESOLVER_INTENT,
            SessionPhase.RESOLVER_INTENT to SessionPhase.RUNNING,
        )

    @Test
    fun onlyDocumentedTransitionsAreWritable() {
        for (from in SessionPhase.entries.filter { it != SessionPhase.RESTORED }) {
            val journal = RecoveryJournal.decode(fixture(from))
            for (to in SessionPhase.entries) {
                if (to != SessionPhase.RESTORED && (from == to || from to to in transitions)) {
                    assertEquals(to, journal.advance(to).phase)
                } else
                    assertFailsWith<IllegalArgumentException>("${from.wire} -> ${to.wire}") {
                        journal.advance(to)
                    }
            }
        }
    }

    @Test
    fun incompleteAndUnknownJournalsCannotAuthorizeRecovery() {
        val raw = fixture(SessionPhase.RUNNING)
        for (key in
            listOf(
                "controllerPid",
                "coreStart",
                "coreDevice",
                "ownershipToken",
                "baseline",
                "tunAfter",
                "resolverIntent",
                "resolverAfter",
            )) {
            assertFails("missing $key") { RecoveryJournal.decode(JsonObject(raw - key)) }
        }
        for ((key, value) in
            listOf(
                "phase" to JsonPrimitive("new-phase"),
                "schemaVersion" to JsonPrimitive(2),
                "controllerPid" to JsonPrimitive(1),
                "corePid" to JsonPrimitive(-1),
            )) {
            assertFails { RecoveryJournal.decode(JsonObject(raw + (key to value))) }
        }
        assertFails {
            RecoveryJournal.decode(
                JsonObject(raw + ("tunPolicy" to TunPolicy.V1.copy(dns = "192.0.2.3").encode()))
            )
        }
    }

    @Test
    fun eachIntentAndCommitSurvivesSimulatedWriterCrashWindows() {
        for ((from, to) in transitions) {
            val initial = RecoveryJournal.decode(fixture(from))
            // A crash before atomic publication leaves the old state and releases no caller action.
            val before = DurableJournal(initial) { error("injected write failure") }
            var released = false
            assertFails {
                before.advance(to)
                released = true
            }
            assertFalse(released)
            assertEquals(initial.json, before.current.json)
            // A rename succeeded but fsync failed: restart uses the new durable image, never old
            // memory.
            var disk = initial.json
            val during =
                DurableJournal(initial) {
                    disk = it
                    error("injected directory sync failure")
                }
            assertFails { during.advance(to) }
            assertEquals(from, during.current.phase)
            assertEquals(to, RecoveryJournal.decode(disk).phase)
            // Once persisted, a crash before/after the external action has the same recovery
            // intent.
            val after = DurableJournal(initial) { disk = it }
            after.advance(to)
            assertEquals(to, after.current.phase)
            assertEquals(to, RecoveryJournal.decode(disk).phase)
        }
    }

    private fun helper(argv: List<String>, state: String = "gated") =
        PendingTool.decode(
            buildJsonObject {
                put("pid", 99)
                put("start", "102")
                put("device", "1")
                put("inode", "3")
                put("gateDevice", "1")
                put("gateInode", "4")
                put("path", argv.first())
                put("argv", JsonArray(argv.map(::JsonPrimitive)))
                put("gateArgv", JsonArray(listOf(JsonPrimitive("/usr/bin/xrayfa"))))
                put("state", state)
            }
        )

    @Test
    fun mutationAuthorizationAcceptsOnlyExactOwnedArguments() {
        val journal = RecoveryJournal.decode(fixture(SessionPhase.TUN_INTENT))
        val address = listOf("/bin/ip", "address", "add", "198.18.0.1/30", "dev", "xrayfa0")
        val commands =
            listOf(address) +
                TunPolicy.V1.routes.map {
                    listOf("/bin/ip", "route", "add", it, "dev", "xrayfa0")
                } +
                listOf(
                    listOf("/sbin/nft", "-f", "/run/xrayfa/artifacts/42-123/ipv6.nft"),
                    listOf("/bin/resolvectl", "dns", "5", "198.18.0.2"),
                    listOf("/bin/resolvectl", "revert", "5"),
                )
        commands.forEach {
            authorizeMutation(journal, helper(it), "/bin/ip", "/sbin/nft", "/bin/resolvectl", false)
        }
        for (bad in
            listOf(
                address + "extra",
                address.dropLast(1) + "eth0",
                listOf("/bin/ip", "route", "del", "default", "dev", "eth0"),
            )) {
            assertFails {
                authorizeMutation(
                    journal,
                    helper(bad),
                    "/bin/ip",
                    "/sbin/nft",
                    "/bin/resolvectl",
                    false,
                )
            }
        }
        assertFails { helper(address, "foreign") }
        assertFails {
            authorizeMutation(
                RecoveryJournal.decode(JsonObject(journal.json - "tunIntent")),
                helper(address),
                "/bin/ip",
                "/sbin/nft",
                "/bin/resolvectl",
                false,
            )
        }
    }

    @Test
    fun anotherPolicyDrivesBothConfigurationAndReadiness() {
        val policy =
            TunPolicy(
                "test0",
                "192.0.2.1",
                30,
                "192.0.2.2",
                1400,
                listOf("0.0.0.0/0"),
                "192.0.2.0/30",
            )
        val out = Json.parseToJsonElement("""{"protocol":"http","settings":{}}""").jsonObject
        val settings =
            runtimeConfig(out, LogLevel.WARNING, policy)
                .getValue("inbounds")
                .jsonArray
                .single()
                .jsonObject
                .getValue("settings")
                .jsonObject
        assertEquals(JsonPrimitive("test0"), settings["name"])
        assertEquals(JsonPrimitive(1400), settings["mtu"])
        assertEquals(JsonArray(listOf(JsonPrimitive("192.0.2.1/30"))), settings["gateway"])
        assertEquals(
            JsonArray(listOf(JsonPrimitive("0.0.0.0/0"))),
            settings["autoSystemRoutingTable"],
        )
        assertTrue(policy.ready(link(policy), routes(policy)))
        assertFalse(TunPolicy.V1.ready(link(policy), routes(policy)))
        assertFalse(policy.ready(link(policy), emptyList()))
    }

    @Test
    fun exitCodesLevelsAndDeadlineAreIndependentOfDisplayText() {
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), ExitCode.entries.map { it.value })
        for (level in LogLevel.entries) for (severity in LogLevel.entries) {
            assertEquals(severity.priority >= level.priority, level.accepts(severity))
            assertEquals(level, LogLevel.parse(level.wire))
        }
        assertEquals(
            ExitCode.USAGE,
            assertFailsWith<CliError> { LogLevel.parse("verbose") }.exitCode,
        )
        assertEquals(
            ExitCode.PERMISSION,
            doctorExit(listOf(BlockingReason("arbitrary text", ProbeStatus.PERMISSION_DENIED))),
        )
        assertEquals(
            ExitCode.DEPENDENCY,
            doctorExit(listOf(BlockingReason("permission in display", ProbeStatus.FAILED))),
        )
        assertEquals(ExitCode.SUCCESS, doctorExit(emptyList()))
        assertEquals(5000L, OperationContext().timeout(100, 5000))
        assertEquals(900L, OperationContext(2000).timeout(100, 5000))
        assertEquals(
            ExitCode.RECOVERY,
            assertFailsWith<CliError> { OperationContext(1000).timeout(100, 5000) }.exitCode,
        )
    }

    @Test
    fun initialLeaseAndStageSpecificEvidenceAreChecked() {
        val minimal =
            JsonObject(
                fixture(SessionPhase.LEASED).filterKeys {
                    it in
                        setOf(
                            "schemaVersion",
                            "controllerPid",
                            "controllerStart",
                            "controllerExe",
                            "nodeId",
                            "uid",
                            "configSha256",
                            "coreSha256",
                            "ownershipToken",
                            "bootId",
                            "namespace",
                            "phase",
                        )
                }
            )
        assertEquals(SessionPhase.LEASED, RecoveryJournal.decode(minimal).phase)
        val required =
            listOf(
                SessionPhase.MANAGEMENT_ROUTE_INTENT to "managementRouteIntent",
                SessionPhase.MANAGEMENT_ROUTES_APPLIED to "managementRoutesAfter",
                SessionPhase.IPV6_INTENT to "nftIntent",
                SessionPhase.IPV6_APPLIED to "nftAfter",
                SessionPhase.CORE_GATED to "corePid",
                SessionPhase.TUN_INTENT to "tunIntent",
                SessionPhase.TUN_READY to "tunAfter",
                SessionPhase.RESOLVER_INTENT to "resolverIntent",
                SessionPhase.RUNNING to "resolverAfter",
            )
        for ((phase, field) in required) assertFails("${phase.wire} missing $field") {
            RecoveryJournal.decode(JsonObject(fixture(phase) - field))
        }
    }
}
