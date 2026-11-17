@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

/** Drop only elapsed lease lifetimes, never actual addresses/routes/DNS configuration. */
internal fun normalizedNetwork(v: JsonElement): JsonElement =
    when (v) {
        is JsonObject ->
            JsonObject(
                v.filterKeys { it !in setOf("valid_life_time", "preferred_life_time", "expires") }
                    .mapValues { normalizedNetwork(it.value) }
            )
        is JsonArray -> JsonArray(v.map { normalizedNetwork(it) })
        else -> v
    }

/** Reconcile the exact short-lived helper before touching resources it may still mutate. */
private fun stopPendingTool(record: JsonObject, context: OperationContext) {
    val pending = RecoveryJournal.decode(record).pendingTool ?: return
    if (pending.state == PendingToolState.REAPED) return
    if (pending.path !in setOf(IP, NFT, RESOLVE))
        fail(ExitCode.RECOVERY, "Journal contains an unknown mutation helper")
    val pid = pending.identity.pid
    if (procStart(pid) == null) return
    val pfd = xf_pidfd_open(pid)
    if (pfd < 0) {
        if (procStart(pid) == null) return
        fail(ExitCode.RECOVERY, "Cannot pin pending mutation helper")
    }
    try {
        val observed = procStart(pid)
        if (observed == null && xf_pidfd_exited(pfd) != 0) return
        if (observed != pending.identity.start.toString())
            fail(ExitCode.RECOVERY, "Pending helper PID was reused")
        if (xf_pidfd_exited(pfd) == 0) {
            val argv =
                try {
                    readBytes("/proc/$pid/cmdline", 65536)!!
                        .decodeToString()
                        .split('\u0000')
                        .filter { it.isNotEmpty() }
                } catch (_: CliError) {
                    emptyList()
                }
            fun matches(device: ULong, inode: ULong, expected: List<String>): Boolean =
                xf_process_exe_matches(pid, device, inode) != 0 && argv == expected
            val identity =
                matches(pending.identity.device, pending.identity.inode, pending.argv) ||
                    matches(pending.gateDevice, pending.gateInode, pending.gateArgv)
            if (!identity) {
                val end = minOf(xf_millis() + 200, context.deadlineMillis ?: Long.MAX_VALUE)
                while (xf_pidfd_exited(pfd) == 0 && xf_millis() < end) usleep(1000u)
                if (xf_pidfd_exited(pfd) == 0)
                    fail(ExitCode.RECOVERY, "Live pending helper identity mismatches journal")
            }
            if (xf_pidfd_exited(pfd) == 0) {
                xf_pidfd_signal(pfd, SIGTERM)
                var end =
                    minOf(xf_millis() + 1000, (context.deadlineMillis ?: Long.MAX_VALUE) - 500)
                while (xf_pidfd_exited(pfd) == 0 && xf_millis() < end) usleep(1000u)
                if (xf_pidfd_exited(pfd) == 0) xf_pidfd_signal(pfd, SIGKILL)
                end = minOf(xf_millis() + 1000, context.deadlineMillis ?: Long.MAX_VALUE)
                while (xf_pidfd_exited(pfd) == 0 && xf_millis() < end) usleep(1000u)
                if (xf_pidfd_exited(pfd) == 0)
                    fail(ExitCode.RECOVERY, "Pending helper exit deadline exceeded")
            }
        }
    } finally {
        close(pfd)
    }
}

internal fun restoreOwned(
    record: JsonObject,
    log: LogSession?,
    core: RunningCore?,
    lease: NetworkLease,
): Boolean {
    val context = OperationContext(xf_millis() + 20000)
    return restoreOwnedWithinDeadline(record, log, core, lease, context)
}

private fun restoreOwnedWithinDeadline(
    record: JsonObject,
    log: LogSession?,
    core: RunningCore?,
    lease: NetworkLease,
    context: OperationContext,
): Boolean {
    val policy = TunPolicy.fromJournal(record)
    var complete = true
    fun step(name: String, block: () -> Unit) {
        try {
            block()
            log?.event("restore", "$name restored", force = true)
            if (log == null) println("$name restored")
        } catch (e: Exception) {
            complete = false
            val reason = if (e is CliError) e.message else "Resource operation failed"
            log?.event(
                "restore",
                "$name incomplete: $reason; resource preserved for inspection",
                severity = LogLevel.ERROR,
                force = true,
            )
            fputs("Recovery incomplete: $name: $reason\n", stderr)
            try {
                val previous = lease.record["recoveryErrors"]?.jsonArray ?: JsonArray(emptyList())
                lease.update(
                    JournalField.RECOVERY_ERRORS to
                        JsonArray(
                            previous +
                                buildJsonObject {
                                    put("resource", name)
                                    put("reason", reason)
                                    put("utc", utc())
                                }
                        )
                )
            } catch (_: Exception) {
                fputs("Cannot persist recovery error; retained original journal\n", stderr)
            }
        }
    }
    var helpersSafe = true
    step("pending-tool") {
        try {
            stopPendingTool(record, context)
        } catch (e: Exception) {
            helpersSafe = false
            throw e
        }
    }
    val expectedTun = record["tunAfter"]?.jsonObject
    val before = record["baseline"]?.jsonObject
    step("resolver") {
        if (record["resolverIntent"]?.jsonPrimitive?.boolean == true) {
            val current = resolverSnapshot(context)
            val baseline = before?.get("resolver")?.jsonObject
            if (baseline == null || current != baseline) {
                val backend = current["backend"]?.jsonPrimitive?.content
                if (backend == "static-resolv.conf") {
                    // Static resolv.conf is host-wide and its ownership is
                    // recorded by the post-mutation snapshot, so it remains
                    // safe to revert after a core crash has removed the TUN.
                    val expectedAfter = record["resolverAfter"]?.jsonObject
                    if (expectedAfter == null || current != expectedAfter)
                        fail(ExitCode.RECOVERY, "Resolver changed outside the last owned step")
                    setSessionDns(lease, "revert", context = context)
                } else {
                    val tun = tunIdentity(context, policy)
                    if (tun == null || expectedTun == null)
                        fail(ExitCode.RECOVERY, "TUN missing before link-scoped resolver revert")
                    if (normalizedNetwork(tun) != normalizedNetwork(expectedTun))
                        fail(ExitCode.RECOVERY, "TUN identity changed")
                    val index = expectedTun.getValue("ifindex").jsonPrimitive.content
                    if (!resolverAlreadyReleased(record, current, index)) {
                        if (!resolverMayRevert(record, current, index))
                            fail(ExitCode.RECOVERY, "Resolver changed outside the last owned step")
                        if (!helpersSafe)
                            fail(
                                ExitCode.RECOVERY,
                                "Previous mutation helper is not safely stopped",
                            )
                        try {
                            setSessionDns(lease, "revert", context = context)
                        } catch (e: CliError) {
                            // A concurrently killed core can close the TUN between the
                            // resolver snapshot and link-scoped resolver revert.
                            if (
                                e.exitCode != ExitCode.EXECUTION ||
                                    tunIdentity(context, policy) != null
                            )
                                throw e
                        }
                    }
                }
            }
        }
    }
    step("core") {
        if (core != null) {
            if (!core.stop(context.deadlineMillis ?: (xf_millis() + 5000)))
                fail(ExitCode.RECOVERY, "Core exit confirmation failed")
        } else {
            val pid = record["corePid"]?.jsonPrimitive?.intOrNull
            if (pid != null && procStart(pid) != null) {
                val pfd = xf_pidfd_open(pid)
                if (pfd < 0) {
                    if (procStart(pid) == null) return@step
                    fail(ExitCode.RECOVERY, "Cannot pin orphan process")
                }
                try {
                    val start = record.getValue("coreStart").jsonPrimitive.content
                    val dev = record.getValue("coreDevice").jsonPrimitive.content.toULong()
                    val ino = record.getValue("coreInode").jsonPrimitive.content.toULong()
                    val observed = procStart(pid)
                    if (observed == null && xf_pidfd_exited(pfd) != 0) return@step
                    if (observed != start) fail(ExitCode.RECOVERY, "Orphan PID start-time mismatch")
                    // A concurrently killed core can lose /proc/exe before recover reads
                    // it. An exited pidfd plus the recorded start time proves death;
                    // there is no live process to signal or non-child waitpid to claim.
                    if (xf_pidfd_exited(pfd) == 0) {
                        val exeMatches = xf_process_exe_matches(pid, dev, ino) != 0
                        val argv =
                            try {
                                readBytes("/proc/$pid/cmdline", 65536)!!
                                    .decodeToString()
                                    .split('\u0000')
                            } catch (_: CliError) {
                                emptyList()
                            }
                        val identityMatches =
                            exeMatches &&
                                record.getValue("configPath").jsonPrimitive.content in argv
                        if (!identityMatches) {
                            val exitRace =
                                minOf(xf_millis() + 200, context.deadlineMillis ?: Long.MAX_VALUE)
                            while (xf_pidfd_exited(pfd) == 0 && xf_millis() < exitRace) usleep(
                                1000u
                            )
                            if (xf_pidfd_exited(pfd) == 0)
                                fail(
                                    ExitCode.RECOVERY,
                                    "Live orphan executable/configuration identity mismatch",
                                )
                        }
                        if (xf_pidfd_exited(pfd) == 0) {
                            if (xf_pidfd_signal(pfd, SIGTERM) != 0 && xf_pidfd_exited(pfd) == 0)
                                fail(ExitCode.RECOVERY, "Cannot stop orphan core")
                            var deadline =
                                minOf(
                                    xf_millis() + 3000,
                                    (context.deadlineMillis ?: Long.MAX_VALUE) - 500,
                                )
                            while (xf_pidfd_exited(pfd) == 0 && xf_millis() < deadline) usleep(
                                10000u
                            )
                            if (xf_pidfd_exited(pfd) == 0) xf_pidfd_signal(pfd, SIGKILL)
                            deadline =
                                minOf(xf_millis() + 2000, context.deadlineMillis ?: Long.MAX_VALUE)
                            while (xf_pidfd_exited(pfd) == 0 && xf_millis() < deadline) usleep(
                                10000u
                            )
                            if (xf_pidfd_exited(pfd) == 0)
                                fail(ExitCode.RECOVERY, "Orphan exit deadline exceeded")
                        }
                    }
                } finally {
                    close(pfd)
                }
            }
        }
    }
    step("tun-and-routes") {
        // Nonpersistent TUN disappears when the final core FD closes. Never delete an ambiguous
        // link.
        if (tunIdentity(context, policy) != null)
            fail(
                ExitCode.RECOVERY,
                "TUN still present; ownership cannot be proven for destructive deletion",
            )
        (record["managementRoutesAfter"] ?: record["managementRouteIntent"])?.jsonArray?.forEach {
            element ->
            val route = element.jsonObject
            if (route["owned"]?.jsonPrimitive?.boolean != true) return@forEach
            val destination = route.getValue("destination").jsonPrimitive.content
            val gateway = route["gateway"]?.jsonPrimitive?.content
            val device = route.getValue("device").jsonPrimitive.content
            val present =
                ipJson("route", "show", "table", "all", context = context).any { item ->
                    val v = item.jsonObject
                    v["dst"]?.jsonPrimitive?.content in setOf(destination, "$destination/32") &&
                        v["dev"]?.jsonPrimitive?.content == device &&
                        v["gateway"]?.jsonPrimitive?.content == gateway
                }
            if (present) {
                val args = mutableListOf("route", "del", "$destination/32")
                gateway?.let { args += listOf("via", it) }
                args += listOf("dev", device)
                lease.mutate(IP, *args.toTypedArray(), context = context)
            }
        }
        if (before != null) {
            if (
                normalizedNetwork(ipJson("route", "show", "table", "all", context = context)) !=
                    normalizedNetwork(before.getValue("routes"))
            )
                fail(ExitCode.RECOVERY, "Routes differ from baseline")
            if (ipJson("rule", context = context) != before.getValue("rules"))
                fail(ExitCode.RECOVERY, "Rules differ from baseline")
            if (resolverSnapshot(context) != before.getValue("resolver"))
                fail(ExitCode.RECOVERY, "Resolver differs from baseline")
        }
    }
    step("ipv6") {
        record["nftTable"]?.jsonPrimitive?.content?.let { table ->
            if (!Regex("xfa_[0-9_]+").matches(table))
                fail(ExitCode.RECOVERY, "Invalid journal nft table name")
            val current = nftState(table, context)
            if (current != null) {
                val after = record["nftAfter"]
                if (after != null) {
                    if (current != after) fail(ExitCode.RECOVERY, "nft after-image changed")
                } else {
                    val intent =
                        record["nftIntent"] ?: fail(ExitCode.RECOVERY, "nft intent is missing")
                    if (nftSemantics(current) != intent)
                        fail(ExitCode.RECOVERY, "nft semantic ownership evidence mismatches intent")
                    val token = record.getValue("ownershipToken").jsonPrimitive.content
                    if (
                        !Regex("[0-9a-f]{32}").matches(token) ||
                            "xrayfa:$token" !in current.toString()
                    )
                        fail(ExitCode.RECOVERY, "nft ownership token is missing")
                }
                if (!helpersSafe)
                    fail(ExitCode.RECOVERY, "Previous mutation helper is not safely stopped")
                lease.mutate(NFT, "delete", "table", "inet", table, context = context)
                if (nftState(table, context) != null) fail(ExitCode.RECOVERY, "nft table retained")
            }
        }
    }
    step("addresses-and-links") {
        if (before != null) {
            if (
                normalizedNetwork(ipJson("address", context = context)) !=
                    normalizedNetwork(before.getValue("addresses"))
            )
                fail(ExitCode.RECOVERY, "Addresses differ from baseline")
            if (ipJson("link", context = context) != before.getValue("links"))
                fail(ExitCode.RECOVERY, "Links differ from baseline")
        }
    }
    return complete
}

internal fun finishJournal(record: JsonObject, log: LogSession?) {
    val uid = record.getValue("uid").jsonPrimitive.content
    val directory = "/var/log/xrayfa/$uid"
    privateDir("/var/log/xrayfa")
    privateDir(directory)
    val completed = RecoveryJournal.decode(record).restored(utc())
    atomicPrivate("$directory/recovery-${getpid()}-${xf_millis()}.json", completed)
    if (unlink(CliPaths.JOURNAL) != 0) fail(ExitCode.RECOVERY, "Cannot remove restored journal")
    val fd = open("/run/xrayfa", O_RDONLY or O_DIRECTORY or O_CLOEXEC)
    if (fd < 0) fail(ExitCode.RECOVERY, "Cannot open recovery directory")
    try {
        if (fsync(fd) != 0) fail(ExitCode.RECOVERY, "Cannot persist journal completion")
    } finally {
        close(fd)
    }
    log?.event("restore", "Journal archived; recovery complete", force = true)
}

internal fun recoverSession(): Int {
    if (geteuid() != 0u) fail(ExitCode.PERMISSION, "recover requires root")
    val caller = callerIdentity()
    privateDir("/run/xrayfa")
    val lock = open("/run/xrayfa/tun.lock", O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
    if (lock < 0 || xf_fd_owned(lock, 0u, 0) == 0) {
        if (lock >= 0) close(lock)
        fail(ExitCode.RECOVERY, "Unsafe recovery lock")
    }
    try {
        if (xf_try_lock(lock) != 0)
            fail(ExitCode.CONFLICT, "Active controller owns the runtime lock")
        if (access(CliPaths.JOURNAL, F_OK) != 0) {
            println("No retained runtime journal")
            return 0
        }
        try {
            ownedFile(CliPaths.JOURNAL)
        } catch (_: Exception) {
            fail(ExitCode.RECOVERY, "Unsafe recovery journal; preserved")
        }
        val record =
            try {
                json.parseToJsonElement(readBytes(CliPaths.JOURNAL)!!.decodeToString()).jsonObject
            } catch (_: Exception) {
                fail(ExitCode.RECOVERY, "Invalid journal; preserved")
            }
        try {
            RecoveryJournal.decode(record)
            if (
                record.getValue("bootId").jsonPrimitive.content != bootId() ||
                    record.getValue("namespace").jsonPrimitive.content != networkNamespace()
            )
                fail(ExitCode.RECOVERY, "Journal boot/namespace mismatch; preserved")
            val pid = record.getValue("controllerPid").jsonPrimitive.int
            if (procStart(pid) == record.getValue("controllerStart").jsonPrimitive.content)
                fail(ExitCode.CONFLICT, "Journal controller remains active")
            validateRecoveryJournal(record)
            val uid = record.getValue("uid").jsonPrimitive.content.toUInt()
            var log: LogSession? = null
            try {
                privateDir("/var/log/xrayfa")
                log =
                    LogSession(
                        record["nodeId"]?.jsonPrimitive?.int,
                        LogLevel.INFO,
                        "/var/log/xrayfa/$uid",
                        uid,
                        nonBlockingTerminal = true,
                    )
            } catch (_: Exception) {
                fputs("Recovery log unavailable; continuing resource restoration\n", stderr)
            }
            val lease = NetworkLease.attached(lock, record)
            val result =
                try {
                    if (!restoreOwned(record, log, null, lease)) 9
                    else {
                        finishJournal(lease.record, log)
                        if (log == null) 11 else 0
                    }
                } finally {
                    log?.close()
                }
            return if (result == 0 && log?.failed == true) 11 else result
        } catch (e: CliError) {
            throw e
        } catch (_: Exception) {
            fail(ExitCode.RECOVERY, "Journal identity/schema validation failed; preserved")
        }
    } finally {
        close(lock)
    }
}

/**
 * Stop only the controller identified by the live journal, then reuse the normal recovery path.
 * This is needed when a non-TTY SSH client disappears: the remote root controller does not receive
 * the terminal's Ctrl+C byte.
 */
internal fun stopSession(): Int {
    if (geteuid() != 0u) fail(ExitCode.PERMISSION, "stop requires root")
    if (access(CliPaths.JOURNAL, F_OK) != 0) {
        println("No active runtime journal")
        return 0
    }
    try {
        ownedFile(CliPaths.JOURNAL)
    } catch (_: Exception) {
        fail(ExitCode.RECOVERY, "Unsafe runtime journal; preserved")
    }
    val record =
        try {
            json.parseToJsonElement(readBytes(CliPaths.JOURNAL)!!.decodeToString()).jsonObject
        } catch (_: Exception) {
            fail(ExitCode.RECOVERY, "Invalid runtime journal; preserved")
        }
    try {
        RecoveryJournal.decode(record)
        if (
            record.getValue("bootId").jsonPrimitive.content != bootId() ||
                record.getValue("namespace").jsonPrimitive.content != networkNamespace()
        )
            fail(ExitCode.RECOVERY, "Journal boot/namespace mismatch; preserved")
        val pid = record.getValue("controllerPid").jsonPrimitive.int
        val expectedStart = record.getValue("controllerStart").jsonPrimitive.content
        val observed = procStart(pid)
        if (observed == null) return recoverSession()
        if (observed != expectedStart)
            fail(ExitCode.RECOVERY, "Journal controller PID has been reused; preserved")
        val expectedExe = record.getValue("controllerExe").jsonPrimitive.content
        fun controllerExeMatches(): Boolean {
            val live = linkTarget("/proc/$pid/exe") ?: return false
            return live == expectedExe || live == "$expectedExe (deleted)"
        }
        if (!controllerExeMatches())
            fail(ExitCode.RECOVERY, "Journal controller executable identity mismatch; preserved")
        val pfd = xf_pidfd_open(pid)
        if (pfd >= 0)
            try {
                if (xf_pidfd_exited(pfd) == 0) {
                    if (xf_pidfd_signal(pfd, SIGINT) != 0)
                        fail(ExitCode.RECOVERY, "Cannot signal active controller; preserved")
                    var deadline = xf_millis() + 10000
                    while (xf_pidfd_exited(pfd) == 0 && xf_millis() < deadline) usleep(10000u)
                    if (xf_pidfd_exited(pfd) == 0) {
                        xf_pidfd_signal(pfd, SIGTERM)
                        deadline = xf_millis() + 5000
                        while (xf_pidfd_exited(pfd) == 0 && xf_millis() < deadline) usleep(10000u)
                    }
                    if (xf_pidfd_exited(pfd) == 0)
                        fail(ExitCode.RECOVERY, "Active controller did not stop; journal preserved")
                }
            } finally {
                close(pfd)
            }
        else {
            // Ubuntu 18.04's 4.15 kernel has no pidfd_open. Re-check the
            // journal identity immediately before falling back to kill(2).
            if (procStart(pid) != expectedStart || !controllerExeMatches())
                fail(ExitCode.RECOVERY, "Active controller identity changed; preserved")
            if (kill(pid, SIGINT) != 0)
                fail(ExitCode.RECOVERY, "Cannot signal active controller; preserved")
            var deadline = xf_millis() + 10000
            while (procStart(pid) != null && xf_millis() < deadline) usleep(10000u)
            if (procStart(pid) != null) {
                if (procStart(pid) != expectedStart || !controllerExeMatches())
                    fail(
                        ExitCode.RECOVERY,
                        "Active controller identity changed while stopping; preserved",
                    )
                kill(pid, SIGTERM)
                deadline = xf_millis() + 5000
                while (procStart(pid) != null && xf_millis() < deadline) usleep(10000u)
            }
            if (procStart(pid) != null)
                fail(ExitCode.RECOVERY, "Active controller did not stop; journal preserved")
        }
        return recoverSession()
    } catch (e: CliError) {
        throw e
    } catch (_: Exception) {
        fail(ExitCode.RECOVERY, "Runtime journal identity validation failed; preserved")
    }
}
