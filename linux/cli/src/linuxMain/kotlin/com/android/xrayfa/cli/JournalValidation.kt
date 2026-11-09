@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import com.android.xrayfa.common.utils.calculateBytesHash
import kotlinx.serialization.json.*
import platform.posix.*

/** Reject malformed/foreign process claims before any recovery network operation. */
internal fun validateRecoveryJournal(record: JsonObject) {
    try {
        val journal = RecoveryJournal.decode(record)
        val controller = journal.controllerPid
        if (journal.phase != SessionPhase.LEASED || "baseline" in record) {
            resolverStructured(
                record.getValue("baseline").jsonObject.getValue("resolver").jsonObject
            )
            val config = record.getValue("configPath").jsonPrimitive.content
            ownedFile(config)
            require(
                calculateBytesHash(readBytes(config)!!) ==
                    record.getValue("configSha256").jsonPrimitive.content
            )
        }
        record["nftTable"]?.jsonPrimitive?.content?.let {
            require(Regex("xfa_${controller}_[0-9]+").matches(it))
            require("nftIntent" in record)
        }
        for (key in listOf("managementRouteIntent", "managementRoutesAfter")) record[key]
            ?.jsonArray
            ?.forEach { element ->
                val route = element.jsonObject
                val destination = route.getValue("destination").jsonPrimitive.content
                require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(destination))
                require(
                    Regex("[a-zA-Z0-9_.-]{1,15}")
                        .matches(route.getValue("device").jsonPrimitive.content)
                )
                route["gateway"]?.jsonPrimitive?.content?.let {
                    require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(it))
                }
                require(route.getValue("owned").jsonPrimitive.boolean)
            }
        journal.pendingTool?.let {
            authorizeMutation(journal, it, IP, NFT, RESOLVE, RESOLVE == SYSTEM_RESOLVE)
        }
        record["corePid"]?.jsonPrimitive?.int?.let { pid ->
            require(pid > 1)
            require(journal.core != null)
            if (procStart(pid) != null) {
                val pfd = xf_pidfd_open(pid)
                if (pfd < 0) {
                    if (procStart(pid) == null) return@let
                    fail(ExitCode.RECOVERY, "Cannot inspect journal core process")
                }
                try {
                    val observed = procStart(pid)
                    if (observed == null && xf_pidfd_exited(pfd) != 0) return@let
                    if (observed != record.getValue("coreStart").jsonPrimitive.content)
                        fail(ExitCode.RECOVERY, "Journal core PID has been reused")
                    if (xf_pidfd_exited(pfd) == 0) {
                        val exe =
                            xf_process_exe_matches(
                                pid,
                                record.getValue("coreDevice").jsonPrimitive.content.toULong(),
                                record.getValue("coreInode").jsonPrimitive.content.toULong(),
                            ) != 0
                        val argv =
                            try {
                                readBytes("/proc/$pid/cmdline", 65536)!!
                                    .decodeToString()
                                    .split('\u0000')
                                    .filter { it.isNotEmpty() }
                            } catch (_: CliError) {
                                emptyList()
                            }
                        val expected =
                            listOf(
                                record.getValue("corePath").jsonPrimitive.content,
                                "run",
                                "-c",
                                record.getValue("configPath").jsonPrimitive.content,
                            )
                        if (!exe || argv != expected) {
                            val deadline = xf_millis() + 200
                            while (xf_pidfd_exited(pfd) == 0 && xf_millis() < deadline) usleep(
                                1000u
                            )
                            if (xf_pidfd_exited(pfd) == 0)
                                fail(
                                    ExitCode.RECOVERY,
                                    "Journal core executable/argv does not match a live owned core",
                                )
                        }
                    }
                } finally {
                    close(pfd)
                }
            }
        }
    } catch (e: CliError) {
        if (e.exitCode == ExitCode.RECOVERY) throw e
        fail(ExitCode.RECOVERY, "Journal identity/file validation failed; preserved")
    } catch (_: Exception) {
        fail(ExitCode.RECOVERY, "Journal schema or resource identity is invalid; preserved")
    }
}
