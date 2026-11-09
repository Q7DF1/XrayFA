@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import platform.posix.*

internal fun redact(text: String, secrets: Set<String> = emptySet()): String {
    var result = clean(text)
    for (s in secrets.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
        result = result.replace(s, "[REDACTED]")
        // Overlong core lines may end in a partial credential.
        for (n in minOf(s.length - 1, result.length) downTo 3) {
            if (result.endsWith(s.take(n))) {
                result = result.dropLast(n) + "[REDACTED]"
                break
            }
        }
    }
    // Token scan stays linear on long unbroken core output; a greedy scheme regex
    // otherwise retries at every byte when no :// delimiter exists.
    result =
        result
            .split(' ')
            .joinToString(" ") { if ("://" in it) "[REDACTED-LINK]" else it }
            .replace(
                Regex("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "[REDACTED-ID]",
            )
            .replace(
                Regex("(?i)\\b(authorization|proxy-authorization)[=: ]+(?:Bearer|Basic) +[^ ,;]+"),
                "$1=[REDACTED]",
            )
            .replace(
                Regex("(?i)\\b(password|privateKey|token|authorization|auth|id)\\b[=: ]+[^ ,;]+"),
                "$1=[REDACTED]",
            )
    return result.take(16384)
}

internal fun secretValues(value: JsonElement): Set<String> {
    val result = mutableSetOf<String>()
    fun visit(v: JsonElement, key: String = "") {
        when (v) {
            is JsonObject -> v.forEach { (k, x) -> visit(x, k) }
            is JsonArray -> v.forEach { visit(it, key) }
            is JsonPrimitive ->
                if (
                    key.lowercase() in
                        setOf(
                            "id",
                            "password",
                            "pass",
                            "user",
                            "auth",
                            "privatekey",
                            "publickey",
                            "pinnedpeercertsha256",
                            "echconfiglist",
                            "mldsa65verify",
                            "token",
                            "authorization",
                            "proxy-authorization",
                        )
                ) {
                    result.add(v.content)
                    if (key.lowercase().contains("authorization") && ' ' in v.content)
                        result.add(v.content.substringAfter(' '))
                }
        }
    }
    visit(value)
    return result
}

internal class LogSession(
    val nodeId: Int?,
    val level: LogLevel,
    private val directory: String = logDir,
    private val callerUid: UInt = getuid(),
    private val nonBlockingTerminal: Boolean = false,
) {
    val session = "${getpid()}-${xf_millis()}"
    val path = "$directory/xrayfa.log"
    var secrets = emptySet<String>()
    private val failure = AtomicInt(0)
    private val failureTime = AtomicLong(0)
    val failedAtMillis
        get() = failureTime.load().takeIf { it != 0L }

    val failed
        get() = failure.load() != 0

    private data class Pending(val sequence: Int, val text: String, val sync: Boolean)

    private val regular = Channel<Pending>(256)
    private val important = Channel<Pending>(64)
    private val boundaries = Channel<Pending>(16)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val dropped = AtomicInt(0)
    private val terminalDropped = AtomicInt(0)
    private val droppedTotal = AtomicLong(0)
    private val terminalTotal = AtomicLong(0)
    private val persistedSequence = AtomicInt(-1)
    private var sequence = 0

    init {
        try {
            if (nonBlockingTerminal && xf_nonblocking_stderr() != 0)
                fail(ExitCode.LOG, "Cannot initialize bounded terminal output")
            privateDir(directory)
            val fd =
                open(
                    path,
                    O_WRONLY or O_CREAT or O_APPEND or O_NOFOLLOW or O_CLOEXEC or O_NONBLOCK,
                    384,
                )
            if (fd < 0) fail(ExitCode.LOG, "Cannot open log file")
            try {
                if (xf_log_file(fd) == 0) fail(ExitCode.LOG, "Unsafe log file type/ownership/mode")
            } finally {
                close(fd)
            }
        } catch (_: Exception) {
            fail(ExitCode.LOG, "Cannot initialize private log file")
        }
    }

    private fun format(stage: String, message: String, source: String, severity: LogLevel) =
        "${utc()} ${severity.wire.uppercase()} $source session=$session uid=$callerUid node=${nodeId ?: "-"} stage=$stage ${redact(message,secrets)}\n"

    private val writer =
        CoroutineScope(Dispatchers.Default).launch {
            for (signal in wake) {
                val queues = listOf(boundaries, important, regular)
                val heads = arrayOfNulls<Pending>(3)
                while (true) {
                    for (i in queues.indices) if (heads[i] == null)
                        heads[i] = queues[i].tryReceive().getOrNull()
                    val next =
                        heads.indices
                            .filter { heads[it] != null }
                            .minByOrNull { heads[it]!!.sequence } ?: break
                    persist(heads[next]!!.text, heads[next]!!.sync)
                    persistedSequence.store(heads[next]!!.sequence)
                    heads[next] = null
                }
                val lost = dropped.exchange(0)
                val terminalLost = terminalDropped.exchange(0)
                if (lost > 0 || terminalLost > 0) {
                    val notice =
                        "${utc()} WARNING CLI session=$session uid=$callerUid node=${nodeId ?: "-"} stage=logs dropped_events=$lost terminal_dropped_events=$terminalLost; bounded queues, errors have reserved capacity\n"
                    if (!nonBlockingTerminal) fputs(notice, stderr)
                    persist(notice)
                }
            }
            if (nonBlockingTerminal && !failed)
                persist(
                    format(
                        "logs",
                        "LOG_QUEUE_END dropped_total=${droppedTotal.load()} terminal_dropped_total=${terminalTotal.load()}",
                        "CLI",
                        LogLevel.INFO,
                    ),
                    true,
                )
        }

    private fun terminal(line: String) {
        if (!nonBlockingTerminal) {
            fputs(line, stderr)
            fflush(stderr)
            return
        }
        // Each bounded terminal record fits PIPE_BUF; the persistent log retains
        // the full redacted line. A slow/closed reader never stalls core supervision.
        val text =
            if (line.length > 1000)
                line.take(1000) + " [terminal line truncated; full text in system log]\n"
            else line
        val bytes = text.encodeToByteArray()
        val written =
            bytes.usePinned { write(STDERR_FILENO, it.addressOf(0), bytes.size.convert()).toInt() }
        if (written != bytes.size) {
            terminalDropped.fetchAndAdd(1)
            terminalTotal.fetchAndAdd(1)
        }
    }

    fun flushBoundary(): Boolean {
        val target = sequence
        event("logs", "Persistence checkpoint before network takeover", force = true)
        val end = xf_millis() + 2000
        while (!failed && persistedSequence.load() < target && xf_millis() < end) usleep(1000u)
        if (persistedSequence.load() < target) {
            failureTime.compareAndSet(0, xf_millis())
            failure.store(1)
        }
        return !failed
    }

    fun close() = runBlocking {
        wake.close()
        if (withTimeoutOrNull(2000) { writer.join() } == null) {
            failureTime.compareAndSet(0, xf_millis())
            failure.store(1)
            writer.cancel()
        }
        regular.close()
        important.close()
        boundaries.close()
    }

    fun event(
        stage: String,
        message: String,
        source: String = "CLI",
        severity: LogLevel = LogLevel.INFO,
        force: Boolean = false,
    ) {
        if (!force && !level.accepts(severity)) return
        val line = format(stage, message, source, severity)
        terminal(line)
        if (failed) return
        val queue =
            when {
                force -> boundaries
                severity == LogLevel.ERROR -> important
                else -> regular
            }
        if (queue.trySend(Pending(sequence++, line, force)).isFailure) {
            dropped.fetchAndAdd(1)
            droppedTotal.fetchAndAdd(1)
        }
        wake.trySend(Unit)
    }

    private fun persist(line: String, sync: Boolean = false) {
        if (failed) return
        try {
            val lock =
                open(
                    "$directory/writer.lock",
                    O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC or O_NONBLOCK,
                    384,
                )
            if (lock < 0) fail(ExitCode.LOG, "Cannot lock log file")
            try {
                if (xf_log_file(lock) == 0)
                    fail(ExitCode.LOG, "Unsafe writer lock type/ownership/mode")
                val deadline = xf_millis() + 1000
                while (xf_try_lock(lock) != 0) {
                    if (xf_millis() >= deadline) fail(ExitCode.LOG, "Log lock deadline exceeded")
                    usleep(1000u)
                }
                ownedFile("$directory/writer.lock")
                if (xf_size(path) + line.encodeToByteArray().size > 5 * 1024 * 1024) {
                    for (i in 3 downTo 1) {
                        val src = if (i == 1) path else "$path.${i-1}"
                        val dst = "$path.$i"
                        if (access(src, F_OK) == 0) {
                            ownedFile(src)
                            ownedFile(dst, missing = true)
                            if (rename(src, dst) != 0) fail(ExitCode.LOG, "Log rotation failed")
                        }
                    }
                }
                val fd =
                    open(
                        path,
                        O_WRONLY or O_APPEND or O_CREAT or O_NOFOLLOW or O_CLOEXEC or O_NONBLOCK,
                        384,
                    )
                if (fd < 0) fail(ExitCode.LOG, "Log open failed")
                try {
                    if (xf_log_file(fd) == 0) fail(ExitCode.LOG, "Unsafe log file descriptor")
                    writeFd(fd, line.encodeToByteArray())
                    if (sync && fsync(fd) != 0) fail(ExitCode.LOG, "Log sync failed")
                } finally {
                    close(fd)
                }
            } finally {
                close(lock)
            }
        } catch (_: Exception) {
            failureTime.compareAndSet(0, xf_millis())
            failure.store(1)
            fputs("Log persistence failed; core output will continue on terminal\n", stderr)
        }
    }
}

/** Read backwards until N lines are collected. Memory stays bounded by N capped lines. */
internal fun tailLogs(path: String, count: Int): List<String> {
    val lines = ArrayDeque<String>()
    if (count == 0) return emptyList()
    for (history in 0..3) {
        val file = if (history == 0) path else "$path.$history"
        if (access(file, F_OK) != 0) {
            if (xf_errno() == ENOENT) continue
            fail(ExitCode.LOG, "Cannot access log history")
        }
        try {
            ownedFile(file)
            val fd = open(file, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
            if (fd < 0) fail(ExitCode.LOG, "Cannot read log history")
            try {
                var position = lseek(fd, 0, SEEK_END)
                if (position < 0) fail(ExitCode.LOG, "Cannot seek log history")
                var pending = ""
                var end = true
                val bytes = ByteArray(8192)
                while (position > 0 && lines.size < count) {
                    val amount = minOf(position, bytes.size.toLong()).toInt()
                    position -= amount
                    if (lseek(fd, position, SEEK_SET) < 0)
                        fail(ExitCode.LOG, "Cannot seek log history")
                    val n = bytes.usePinned { read(fd, it.addressOf(0), amount.convert()).toInt() }
                    if (n != amount) fail(ExitCode.LOG, "Cannot read log history")
                    pending =
                        buildString {
                            for (i in 0 until n) append((bytes[i].toInt() and 255).toChar())
                        } + pending
                    while ('\n' in pending && lines.size < count) {
                        val at = pending.lastIndexOf('\n')
                        val raw = pending.substring(at + 1)
                        pending = pending.substring(0, at)
                        if (!end || raw.isNotEmpty())
                            lines.addFirst(
                                redact(
                                    raw.take(16384)
                                        .map { it.code.toByte() }
                                        .toByteArray()
                                        .decodeToString()
                                )
                            )
                        end = false
                    }
                    // Writer lines are capped. Bound reads even if an external writer adds an
                    // unbounded line.
                    if (pending.length > 16384) pending = pending.take(16384)
                }
                if (position == 0L && lines.size < count && pending.isNotEmpty())
                    lines.addFirst(
                        redact(pending.map { it.code.toByte() }.toByteArray().decodeToString())
                    )
            } finally {
                close(fd)
            }
        } catch (_: Exception) {
            fail(ExitCode.LOG, "Cannot read private log history")
        }
        if (lines.size == count) break
    }
    return lines.toList()
}

internal fun showLogs(tail: Int, follow: Boolean, directory: String = logDir) {
    val path = "$directory/xrayfa.log"
    if (access(directory, F_OK) != 0) fail(ExitCode.LOG, "Log directory does not exist")
    if (xf_owned(directory, 1) != 1) fail(ExitCode.LOG, "Unsafe log directory")
    if ((0..3).none { access(if (it == 0) path else "$path.$it", F_OK) == 0 })
        fail(ExitCode.LOG, "No log files found")
    for (i in 0..3) {
        val file = if (i == 0) path else "$path.$i"
        if (access(file, F_OK) == 0 && xf_owned(file, 0) != 1)
            fail(ExitCode.LOG, "Unsafe log history")
    }
    val lock =
        open(
            "$directory/writer.lock",
            O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC or O_NONBLOCK,
            384,
        )
    if (lock < 0) fail(ExitCode.LOG, "Cannot lock log history")
    var fd = -1
    var inode = 0uL
    var offset = 0L
    try {
        if (xf_lock(lock) != 0) fail(ExitCode.LOG, "Cannot lock log history")
        ownedFile("$directory/writer.lock")
        tailLogs(path, tail).forEach(::println)
        if (follow && access(path, F_OK) == 0) {
            fd = open(path, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
            if (fd < 0) fail(ExitCode.LOG, "Cannot open log for follow")
            inode = xf_inode(path)
            offset = lseek(fd, 0, SEEK_END)
            if (offset < 0) fail(ExitCode.LOG, "Cannot seek log for follow")
        }
    } finally {
        close(lock)
    }
    if (!follow) return
    var pending = ""
    val bytes = ByteArray(4096)
    try {
        while (xf_interrupted() == 0) {
            if (fd >= 0) {
                if (xf_inode(path) == inode && xf_size(path) < offset) {
                    if (lseek(fd, 0, SEEK_SET) < 0) fail(ExitCode.LOG, "Cannot seek truncated log")
                    offset = 0
                    pending = ""
                }
                while (true) {
                    val n =
                        bytes.usePinned { read(fd, it.addressOf(0), bytes.size.convert()).toInt() }
                    if (n == 0) break
                    if (n < 0) {
                        if (xf_errno() == EINTR) continue
                        fail(ExitCode.LOG, "Log follow read failed")
                    }
                    offset += n
                    pending += buildString {
                        for (i in 0 until n) append((bytes[i].toInt() and 255).toChar())
                    }
                    while ('\n' in pending) {
                        val line =
                            pending
                                .substringBefore('\n')
                                .map { it.code.toByte() }
                                .toByteArray()
                                .decodeToString()
                        println(redact(line))
                        pending = pending.substringAfter('\n')
                    }
                    if (pending.length > 16384) {
                        println(redact(pending.take(16384)))
                        pending = ""
                    }
                }
            }
            val current = xf_inode(path)
            if (current != 0uL && current != inode) {
                // Drain the previous inode first, then start the replacement at offset zero.
                ownedFile(path)
                val next = open(path, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
                if (next < 0) fail(ExitCode.LOG, "Cannot follow replacement log")
                if (fd >= 0) close(fd)
                fd = next
                inode = current
                offset = 0
                pending = ""
            } else usleep(100000u)
        }
    } finally {
        if (fd >= 0) close(fd)
    }
}
