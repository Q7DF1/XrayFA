@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

internal fun procStart(pid: Int): String? =
    try {
        readBytes("/proc/$pid/stat", 8192)!!
            .decodeToString()
            .substringAfterLast(") ")
            .split(' ')[19]
    } catch (_: CliError) {
        null
    }

internal fun linkTarget(path: String): String? = memScoped {
    val b = allocArray<ByteVar>(4096)
    val n = readlink(path, b, 4095u).toInt()
    if (n < 0) null
    else {
        b[n] = 0
        b.toKString()
    }
}

internal fun leaseNonce(): String {
    val bytes = UByteArray(16)
    if (bytes.usePinned { xf_random_bytes(it.addressOf(0), bytes.size) } != 0)
        fail(ExitCode.IO, "Cannot generate lease ownership token")
    return bytes.joinToString("") { it.toString(16).padStart(2, '0') }
}

internal fun bootId() = readBytes("/proc/sys/kernel/random/boot_id", 128)!!.decodeToString().trim()

internal fun networkNamespace() =
    linkTarget("/proc/self/ns/net")
        ?: fail(ExitCode.DEPENDENCY, "Cannot identify network namespace")

internal fun atomicPrivate(path: String, value: JsonObject) {
    val parent = path.substringBeforeLast('/')
    val tmp = "$parent/.journal-${getpid()}-${xf_millis()}"
    try {
        newFile(tmp, json.encodeToString(JsonObject.serializer(), value) + "\n")
        if (rename(tmp, path) != 0) fail(ExitCode.IO, "Journal atomic replacement failed")
        val fd = open(parent, O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
        if (fd < 0) fail(ExitCode.IO, "Cannot open journal directory")
        try {
            if (fsync(fd) != 0) fail(ExitCode.IO, "Journal directory sync failed")
        } finally {
            close(fd)
        }
    } finally {
        unlink(tmp)
    }
}

/** Global lease acquisition has no network side effects; an old journal is never overwritten. */
internal class NetworkLease private constructor(private val fd: Int, record: JsonObject) {
    val path = CliPaths.JOURNAL
    private val journal = DurableJournal(RecoveryJournal.decode(record)) { atomicPrivate(path, it) }
    val record
        get() = journal.current.json

    fun update(vararg fields: Pair<JournalField, JsonElement>) {
        journal.update(*fields)
    }

    fun advance(phase: SessionPhase, vararg fields: Pair<JournalField, JsonElement>) {
        journal.advance(phase, *fields)
    }

    /** Every network-mutating helper is gated until its exact identity is durable. */
    fun mutate(
        path: String,
        vararg arguments: String,
        context: OperationContext = OperationContext(),
    ): String {
        if (path !in setOf(IP, NFT, RESOLVE))
            fail(ExitCode.IO, "Unrecognized network mutation helper")
        val executable = open(path, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
        if (executable < 0) fail(ExitCode.EXECUTION, "Cannot open network mutation helper")
        try {
            if (xf_trusted_core(executable) == 0)
                fail(ExitCode.PERMISSION, "Unsafe network mutation helper")
            val args = arguments.toList()
            val timeout = context.timeout(xf_millis(), 5000)
            val result =
                coreProcess(
                    path,
                    args,
                    null,
                    timeout,
                    ignoreSignals = true,
                    gate =
                        ProcessGate(executable) { pid ->
                            val self = open("/proc/self/exe", O_RDONLY or O_CLOEXEC)
                            if (self < 0)
                                fail(ExitCode.IO, "Cannot pin controller executable identity")
                            val identity =
                                try {
                                    buildJsonObject {
                                        put("pid", pid)
                                        put(
                                            "start",
                                            procStart(pid)
                                                ?: fail(
                                                    ExitCode.IO,
                                                    "Cannot identify gated network helper",
                                                ),
                                        )
                                        put("path", path)
                                        put("device", xf_fd_device(executable).toString())
                                        put("inode", xf_fd_inode(executable).toString())
                                        put("gateDevice", xf_fd_device(self).toString())
                                        put("gateInode", xf_fd_inode(self).toString())
                                        put(
                                            "argv",
                                            JsonArray((listOf(path) + args).map(::JsonPrimitive)),
                                        )
                                        put(
                                            "gateArgv",
                                            JsonArray(
                                                readBytes("/proc/self/cmdline", 65536)!!
                                                    .decodeToString()
                                                    .split('\u0000')
                                                    .filter { it.isNotEmpty() }
                                                    .map(::JsonPrimitive)
                                            ),
                                        )
                                        put("state", PendingToolState.GATED.wire)
                                        put("intentUtc", utc())
                                    }
                                } finally {
                                    close(self)
                                }
                            val pending = PendingTool.decode(identity)
                            authorizeMutation(
                                journal.current,
                                pending,
                                IP,
                                NFT,
                                RESOLVE,
                                RESOLVE == SYSTEM_RESOLVE,
                            )
                            update(JournalField.PENDING_TOOL to identity)
                        },
                )
            val old = record.getValue("pendingTool").jsonObject
            update(
                JournalField.PENDING_TOOL to
                    JsonObject(
                        old.toMutableMap().apply {
                            put("state", JsonPrimitive(PendingToolState.REAPED.wire))
                            put("exit", JsonPrimitive(result.exit))
                            put("timedOut", JsonPrimitive(result.timedOut))
                        }
                    )
            )
            if (result.exit != 0 || result.timedOut)
                fail(
                    ExitCode.EXECUTION,
                    "Network mutation helper failed: $path ${args.take(2).joinToString(" ")}",
                )
            return result.output.trim()
        } finally {
            close(executable)
        }
    }

    fun close() {
        close(fd)
    }

    companion object {
        fun attached(fd: Int, record: JsonObject) = NetworkLease(fd, record)

        fun acquire(
            caller: CallerIdentity,
            nodeId: Int,
            configHash: String,
            coreHash: String,
        ): NetworkLease {
            if (geteuid() != 0u) fail(ExitCode.PERMISSION, "TUN sessions require root")
            privateDir("/run/xrayfa")
            val fd = open("/run/xrayfa/tun.lock", O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
            if (fd < 0) fail(ExitCode.IO, "Cannot open runtime lock")
            try {
                if (xf_fd_owned(fd, 0u, 0) == 0) fail(ExitCode.IO, "Unsafe runtime lock")
                if (xf_try_lock(fd) != 0)
                    fail(ExitCode.CONFLICT, "An active TUN session owns the global lock")
                if (access(CliPaths.JOURNAL, F_OK) == 0)
                    fail(
                        ExitCode.RECOVERY,
                        "Previous journal retained; use recover before starting a session",
                    )
                val record = buildJsonObject {
                    put("schemaVersion", 1)
                    put("bootId", bootId())
                    put("namespace", networkNamespace())
                    put("controllerPid", getpid())
                    put(
                        "controllerStart",
                        procStart(getpid()) ?: fail(ExitCode.IO, "Cannot identify controller"),
                    )
                    put(
                        "controllerExe",
                        linkTarget("/proc/self/exe") ?: fail(ExitCode.IO, "Cannot identify CLI"),
                    )
                    put("uid", caller.uid.toLong())
                    put("nodeId", nodeId)
                    put("configSha256", configHash)
                    put("coreSha256", coreHash)
                    put("ownershipToken", leaseNonce())
                    put("phase", SessionPhase.LEASED.wire)
                    put("tunPolicy", TunPolicy.CURRENT.encode())
                    put("createdUtc", utc())
                    putJsonArray("intents") {}
                }
                try {
                    atomicPrivate(CliPaths.JOURNAL, record)
                } catch (e: Exception) {
                    // The journal is ours only after the pre-existing-path check
                    // above. If atomicPrivate renamed the file and then failed
                    // its directory fsync, remove that just-created lease before
                    // returning the pre-network exit-10 error; never touch an
                    // older journal belonging to another session.
                    if (access(CliPaths.JOURNAL, F_OK) == 0) unlink(CliPaths.JOURNAL)
                    throw e
                }
                return NetworkLease(fd, record)
            } catch (e: Exception) {
                close(fd)
                throw e
            }
        }
    }
}

internal fun nodeSnapshot(caller: CallerIdentity, id: Int): JsonObject {
    val fd = xf_snapshot_open_path(caller.profile, caller.uid)
    if (fd < 0)
        fail(
            if (xf_errno() == ENOENT) ExitCode.NODE_MISSING else ExitCode.IO,
            "Cannot open caller's private node snapshot",
        )
    val text =
        try {
            val bytes = ByteArray(16 * 1024 * 1024 + 1)
            var total = 0
            while (total < bytes.size) {
                val n =
                    bytes.usePinned {
                        read(fd, it.addressOf(total), (bytes.size - total).convert()).toInt()
                    }
                if (n == 0) break
                if (n < 0) {
                    if (xf_errno() == EINTR) continue
                    fail(ExitCode.IO, "Node snapshot read failed")
                }
                total += n
            }
            if (total >= bytes.size) fail(ExitCode.IO, "Node snapshot exceeds size limit")
            bytes.copyOf(total).decodeToString()
        } finally {
            close(fd)
        }
    val store =
        try {
            NodeStoreCodec.decode(text)
        } catch (_: Exception) {
            fail(ExitCode.IO, "Unsupported node snapshot schema")
        }
    return NodeStoreCodec.find(store, id) ?: fail(ExitCode.NODE_MISSING, "Node ID does not exist")
}
