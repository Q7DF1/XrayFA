@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import platform.posix.*

internal const val VERSION = "0.2.0-runtime-dev"
private val help =
    """XrayFA $VERSION — Linux TUN client (runtime development)
Usage:
  xrayfa add (--link URI | --link-file FILE)
  xrayfa ls
  xrayfa doctor [--core PATH] [--json]
  sudo xrayfa run ID [--core PATH] [--log-level debug|info|warning|error]
  sudo xrayfa stop
  sudo xrayfa recover
  xrayfa check ID --output FILE [--core PATH] [--log-level debug|info|warning|error]
  xrayfa logs [--system] [--tail N] [--follow]
  xrayfa --version | --help
run requires root, stays in the foreground and owns a TUN session. Ctrl+C or `stop` stops it.
System logs: /var/log/xrayfa/<UID>; recovery journal: /run/xrayfa/session.json
Private data: ~/.local/share/xrayfa; logs: ~/.local/state/xrayfa/logs
XRAYFA_HOME isolates add/ls/check/user logs. Runtime commands use the passwd caller identity.
"""

internal fun options(
    args: List<String>,
    allowed: Set<String>,
    flags: Set<String> = emptySet(),
): Map<String, String> {
    val result = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i++]
        if (key !in allowed || key in result) fail(ExitCode.USAGE, "Unknown or repeated option")
        result[key] =
            if (key in flags) "true"
            else {
                if (i == args.size || args[i].startsWith("--"))
                    fail(ExitCode.USAGE, "Missing option value")
                args[i++]
            }
    }
    return result
}

fun main(args: Array<String>) {
    umask(63u)
    xf_signals()
    val code =
        try {
            runBlocking {
                when (args.firstOrNull()) {
                    null,
                    "--help",
                    "-h" -> {
                        if (args.size > 1) fail(ExitCode.USAGE, "Unexpected arguments")
                        println(help)
                        0
                    }
                    "--version" -> {
                        if (args.size != 1) fail(ExitCode.USAGE, "Unexpected arguments")
                        println("XrayFA $VERSION")
                        0
                    }
                    "run" -> {
                        val id =
                            args.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
                                ?: fail(ExitCode.USAGE, "Positive node ID required")
                        val opt = options(args.drop(2), setOf("--core", "--log-level"))
                        val level = LogLevel.parse(opt["--log-level"] ?: "info")
                        runNode(id, opt["--core"], level)
                    }
                    "stop" -> {
                        if (args.size != 1) fail(ExitCode.USAGE, "Unexpected arguments")
                        stopSession()
                    }
                    "recover" -> {
                        if (args.size != 1) fail(ExitCode.USAGE, "Unexpected arguments")
                        recoverSession()
                    }
                    "doctor" -> {
                        val opt = options(args.drop(1), setOf("--core", "--json"), setOf("--json"))
                        doctor(opt["--core"], "--json" in opt)
                    }
                    "add" -> {
                        val opt = options(args.drop(1), setOf("--link", "--link-file"))
                        if (opt.size != 1)
                            fail(ExitCode.USAGE, "Exactly one link source is required")
                        val link =
                            opt["--link"]
                                ?: readBytes(absolute(opt.getValue("--link-file")), 1048576)!!
                                    .decodeToString()
                                    .trim()
                                    .also {
                                        if (it.lines().size != 1 || it.isEmpty())
                                            fail(
                                                ExitCode.USAGE,
                                                "Link file must contain exactly one link",
                                            )
                                    }
                        val node =
                            try {
                                NodeParser().parse(link)
                            } catch (_: Exception) {
                                fail(
                                    ExitCode.CONFIGURATION,
                                    "Invalid or unsupported share link/parameters",
                                )
                            }
                        val record = storeLock { directory ->
                            val store = loadStore(directory)
                            val id = store.getValue("nextId").jsonPrimitive.int
                            if (id == Int.MAX_VALUE) fail(ExitCode.IO, "Node ID range exhausted")
                            val n = nodeRecord(node, id)
                            saveStore(
                                buildJsonObject {
                                    put("schemaVersion", 1)
                                    put("nextId", id + 1)
                                    put("nodes", JsonArray(store.getValue("nodes").jsonArray + n))
                                },
                                directory,
                            )
                            n
                        }
                        println("Added node ${summary(record)}")
                        0
                    }
                    "ls" -> {
                        if (args.size != 1) fail(ExitCode.USAGE, "Unexpected arguments")
                        storeLock { directory ->
                                loadStore(directory).getValue("nodes").jsonArray.map {
                                    summary(it.jsonObject)
                                }
                            }
                            .forEach(::println)
                        0
                    }
                    "check" -> {
                        val id =
                            args.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
                                ?: fail(ExitCode.USAGE, "Positive node ID required")
                        val opt = options(args.drop(2), setOf("--output", "--core", "--log-level"))
                        val output = opt["--output"] ?: fail(ExitCode.USAGE, "--output is required")
                        val level = LogLevel.parse(opt["--log-level"] ?: "info")
                        checkNode(id, output, opt["--core"], level)
                    }
                    "logs" -> {
                        val opt =
                            options(
                                args.drop(1),
                                setOf("--tail", "--follow", "--system"),
                                setOf("--follow", "--system"),
                            )
                        val tail =
                            opt["--tail"]?.toIntOrNull()
                                ?: if ("--tail" in opt) fail(ExitCode.USAGE, "Invalid tail count")
                                else 100
                        if (tail !in 0..10000) fail(ExitCode.USAGE, "Tail count must be 0..10000")
                        val directory =
                            if ("--system" in opt) {
                                if (geteuid() != 0u)
                                    fail(ExitCode.PERMISSION, "System logs require root")
                                callerIdentity().systemLogs
                            } else logDir
                        try {
                            showLogs(tail, "--follow" in opt, directory)
                        } catch (_: Exception) {
                            fail(ExitCode.LOG, "Cannot read/follow private logs")
                        }
                        0
                    }
                    else -> fail(ExitCode.USAGE, "Unknown command; see --help")
                }
            }
        } catch (e: CliError) {
            fputs("Error: ${e.message}\n", stderr)
            e.code
        } catch (_: Exception) {
            fputs("Error: operation failed\n", stderr)
            10
        }
    exit(code)
}
