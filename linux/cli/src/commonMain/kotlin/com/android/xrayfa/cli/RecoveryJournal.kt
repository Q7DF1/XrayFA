package com.android.xrayfa.cli

import kotlinx.serialization.json.*

internal enum class SessionPhase(val wire: String) {
    LEASED("leased"),
    MANAGEMENT_ROUTE_INTENT("management-route-intent"),
    MANAGEMENT_ROUTES_APPLIED("management-routes-applied"),
    IPV6_INTENT("ipv6-intent"),
    IPV6_APPLIED("ipv6-applied"),
    CORE_INTENT("core-intent"),
    CORE_GATED("core-gated"),
    TUN_INTENT("tun-intent"),
    TUN_READY("tun-ready"),
    RESOLVER_INTENT("resolver-intent"),
    RUNNING("Running"),
    RESTORED("restored");

    fun permits(next: SessionPhase): Boolean =
        next == this ||
            when (this) {
                LEASED -> next in setOf(MANAGEMENT_ROUTE_INTENT, IPV6_INTENT)
                MANAGEMENT_ROUTE_INTENT -> next == MANAGEMENT_ROUTES_APPLIED
                MANAGEMENT_ROUTES_APPLIED -> next == IPV6_INTENT
                IPV6_INTENT -> next == IPV6_APPLIED
                IPV6_APPLIED -> next == CORE_INTENT
                CORE_INTENT -> next == CORE_GATED
                CORE_GATED -> next in setOf(TUN_INTENT, TUN_READY)
                TUN_INTENT -> next == TUN_READY
                TUN_READY -> next == RESOLVER_INTENT
                RESOLVER_INTENT -> next == RUNNING
                RUNNING,
                RESTORED -> false
            }

    companion object {
        fun parse(value: String): SessionPhase =
            entries.firstOrNull { it.wire == value } ?: error("Unknown journal phase")
    }
}

/** Internal writes use named fields; wire names remain schema-1 compatible. */
internal enum class JournalField(val wire: String) {
    BASELINE("baseline"),
    CONFIG_PATH("configPath"),
    CORE_PATH("corePath"),
    CORE_DEVICE("coreDevice"),
    CORE_INODE("coreInode"),
    UNDERLAY("underlay"),
    CORE_PID("corePid"),
    CORE_START("coreStart"),
    TUN_AFTER("tunAfter"),
    TUN_INTENT("tunIntent"),
    ROUTES_AFTER("routesAfter"),
    RULES_AFTER("rulesAfter"),
    RESOLVER_INTENT("resolverIntent"),
    RESOLVER_STEP_BEFORE("resolverStepBefore"),
    RESOLVER_STEP_INTENT("resolverStepIntent"),
    RESOLVER_CLEANUP_AFTER("resolverCleanupAfter"),
    RESOLVER_RESTORED("resolverRestored"),
    RESOLVER_AFTER("resolverAfter"),
    MANAGEMENT_ROUTE_INTENT("managementRouteIntent"),
    MANAGEMENT_ROUTES_AFTER("managementRoutesAfter"),
    NFT_TABLE("nftTable"),
    NFT_INTENT("nftIntent"),
    NFT_AFTER("nftAfter"),
    PENDING_TOOL("pendingTool"),
    RECOVERY_ERRORS("recoveryErrors"),
}

internal data class ProcessIdentity(
    val pid: Int,
    val start: ULong,
    val device: ULong,
    val inode: ULong,
) {
    init {
        require(pid > 1 && start > 0uL && device > 0uL && inode > 0uL)
    }
}

internal enum class PendingToolState(val wire: String) {
    GATED("gated"),
    REAPED("reaped");

    companion object {
        fun parse(value: String) =
            entries.firstOrNull { it.wire == value } ?: error("Unknown helper state")
    }
}

internal data class PendingTool(
    val identity: ProcessIdentity,
    val gateDevice: ULong,
    val gateInode: ULong,
    val path: String,
    val argv: List<String>,
    val gateArgv: List<String>,
    val state: PendingToolState,
) {
    init {
        require(gateDevice > 0uL && gateInode > 0uL)
        require(path.startsWith('/') && argv.firstOrNull() == path && gateArgv.isNotEmpty())
    }

    companion object {
        fun decode(raw: JsonObject) =
            PendingTool(
                ProcessIdentity(
                    raw.getValue("pid").jsonPrimitive.int,
                    raw.unsigned("start"),
                    raw.unsigned("device"),
                    raw.unsigned("inode"),
                ),
                raw.unsigned("gateDevice"),
                raw.unsigned("gateInode"),
                raw.getValue("path").jsonPrimitive.content,
                raw.getValue("argv").jsonArray.map { it.jsonPrimitive.content },
                raw.getValue("gateArgv").jsonArray.map { it.jsonPrimitive.content },
                PendingToolState.parse(raw.getValue("state").jsonPrimitive.content),
            )
    }
}

private fun JsonObject.unsigned(key: String) = getValue(key).jsonPrimitive.content.toULong()

/**
 * Preserve raw network evidence/unknown extension fields while checking the internal protocol once.
 */
internal class RecoveryJournal private constructor(val json: JsonObject) {
    val phase = SessionPhase.parse(json.getValue("phase").jsonPrimitive.content)
    val controllerPid = json.getValue("controllerPid").jsonPrimitive.int
    val controllerStart = json.unsigned("controllerStart")
    val controllerExe = json.getValue("controllerExe").jsonPrimitive.content
    val policy = TunPolicy.fromJournal(json)
    val core: ProcessIdentity? =
        json["corePid"]?.let {
            ProcessIdentity(
                it.jsonPrimitive.int,
                json.unsigned("coreStart"),
                json.unsigned("coreDevice"),
                json.unsigned("coreInode"),
            )
        }
    val pendingTool = json["pendingTool"]?.jsonObject?.let(PendingTool::decode)

    init {
        require(json.getValue("schemaVersion").jsonPrimitive.int == 1)
        require(controllerPid > 1 && controllerStart > 0uL && controllerExe.startsWith('/'))
        require(json.getValue("nodeId").jsonPrimitive.int > 0)
        json.getValue("uid").jsonPrimitive.content.toUInt()
        for (key in listOf("configSha256", "coreSha256")) {
            require(Regex("[0-9a-f]{64}").matches(json.getValue(key).jsonPrimitive.content))
        }
        require(
            Regex("[0-9a-f]{32}").matches(json.getValue("ownershipToken").jsonPrimitive.content)
        )
        if (phase != SessionPhase.LEASED || "baseline" in json) {
            val baseline = json.getValue("baseline").jsonObject
            for (key in listOf("routes", "rules", "links", "addresses")) baseline
                .getValue(key)
                .jsonArray
            baseline.getValue("resolver").jsonObject
            require(
                Regex("/run/xrayfa/artifacts/$controllerPid-[0-9]+/final\\.json")
                    .matches(json.getValue("configPath").jsonPrimitive.content)
            )
            require(json.getValue("corePath").jsonPrimitive.content.startsWith('/'))
            require(json.unsigned("coreDevice") > 0uL && json.unsigned("coreInode") > 0uL)
        }
        if (
            phase in
                setOf(
                    SessionPhase.CORE_GATED,
                    SessionPhase.TUN_INTENT,
                    SessionPhase.TUN_READY,
                    SessionPhase.RESOLVER_INTENT,
                    SessionPhase.RUNNING,
                )
        )
            require(core != null)
        when (phase) {
            SessionPhase.MANAGEMENT_ROUTE_INTENT -> json.getValue("managementRouteIntent").jsonArray
            SessionPhase.MANAGEMENT_ROUTES_APPLIED ->
                json.getValue("managementRoutesAfter").jsonArray
            else -> Unit
        }
        if (
            phase in
                setOf(
                    SessionPhase.IPV6_INTENT,
                    SessionPhase.IPV6_APPLIED,
                    SessionPhase.CORE_INTENT,
                    SessionPhase.CORE_GATED,
                    SessionPhase.TUN_INTENT,
                    SessionPhase.TUN_READY,
                    SessionPhase.RESOLVER_INTENT,
                    SessionPhase.RUNNING,
                )
        ) {
            require(
                Regex("xfa_${controllerPid}_[0-9]+")
                    .matches(json.getValue("nftTable").jsonPrimitive.content)
            )
            json.getValue("nftIntent").jsonObject
        }
        if (
            phase in
                setOf(
                    SessionPhase.IPV6_APPLIED,
                    SessionPhase.CORE_INTENT,
                    SessionPhase.CORE_GATED,
                    SessionPhase.TUN_INTENT,
                    SessionPhase.TUN_READY,
                    SessionPhase.RESOLVER_INTENT,
                    SessionPhase.RUNNING,
                )
        )
            json.getValue("nftAfter").jsonObject
        if (phase == SessionPhase.TUN_INTENT) json.getValue("tunIntent").jsonObject
        if (
            phase in
                setOf(SessionPhase.TUN_READY, SessionPhase.RESOLVER_INTENT, SessionPhase.RUNNING)
        )
            json.getValue("tunAfter").jsonObject
        if (phase in setOf(SessionPhase.RESOLVER_INTENT, SessionPhase.RUNNING))
            require(json.getValue("resolverIntent").jsonPrimitive.boolean)
        if (phase == SessionPhase.RUNNING) {
            json.getValue("routesAfter").jsonArray
            json.getValue("rulesAfter").jsonArray
            json.getValue("resolverAfter").jsonObject
        }
    }

    fun update(vararg fields: Pair<JournalField, JsonElement>): RecoveryJournal =
        decode(JsonObject(json + fields.associate { it.first.wire to it.second }))

    fun advance(
        next: SessionPhase,
        vararg fields: Pair<JournalField, JsonElement>,
    ): RecoveryJournal {
        require(next != SessionPhase.RESTORED && phase.permits(next)) {
            "Illegal journal transition: ${phase.wire} -> ${next.wire}"
        }
        return decode(
            JsonObject(
                json +
                    fields.associate { it.first.wire to it.second } +
                    ("phase" to JsonPrimitive(next.wire))
            )
        )
    }

    /** Only the recovery coordinator may archive a completed resource restoration. */
    fun restored(utc: String): JsonObject =
        JsonObject(
            json +
                mapOf(
                    "phase" to JsonPrimitive(SessionPhase.RESTORED.wire),
                    "restoredUtc" to JsonPrimitive(utc),
                )
        )

    companion object {
        fun decode(json: JsonObject): RecoveryJournal = RecoveryJournal(json)
    }
}

/** Publish in-memory state only after the atomic writer returns successfully. */
internal class DurableJournal(initial: RecoveryJournal, private val write: (JsonObject) -> Unit) {
    var current: RecoveryJournal = initial
        private set

    fun update(vararg fields: Pair<JournalField, JsonElement>) = persist(current.update(*fields))

    fun advance(phase: SessionPhase, vararg fields: Pair<JournalField, JsonElement>) =
        persist(current.advance(phase, *fields))

    private fun persist(next: RecoveryJournal) {
        write(next.json)
        current = next
    }
}
