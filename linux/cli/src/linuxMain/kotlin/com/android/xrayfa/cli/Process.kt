@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import platform.posix.*

internal data class ProcessGate(val executableFd: Int, val beforeRelease: (Int) -> Unit)

internal data class ProcessResult(
    val exit: Int,
    val timedOut: Boolean,
    val interrupted: Boolean,
    val output: String,
    val observedAtMillis: Long,
)

/** posix_spawn with two concurrently drained nonblocking pipes, bounded lines and process group. */
internal fun coreProcess(
    path: String,
    args: List<String>,
    log: LogSession?,
    timeoutMillis: Long = 30000,
    ignoreSignals: Boolean = false,
    gate: ProcessGate? = null,
    maxLineLength: Int = 16384,
    maxCapturedBytes: Int = 65536,
): ProcessResult = memScoped {
    val out = alloc<IntVar>()
    val err = alloc<IntVar>()
    fun strings(items: List<String>): CPointer<CPointerVar<ByteVar>> {
        val array = allocArray<CPointerVar<ByteVar>>(items.size + 1)
        items.forEachIndexed { i, s -> array[i] = s.cstr.ptr }
        array[items.size] = null
        return array
    }
    val argv = strings(listOf(path) + args)
    // Deliberately no inherited XRAY_* config/confdir discovery or proxy variables.
    val env =
        strings(
            listOf(
                "PATH=/usr/bin:/bin",
                "LANG=C",
                "LC_ALL=C",
                "XRAY_LOCATION_ASSET=${path.substringBeforeLast('/')}",
            )
        )
    val release = alloc<IntVar>()
    release.value = -1
    val pid =
        if (gate == null) xf_spawn(path, argv, env, out.ptr, err.ptr)
        else xf_spawn_gated(gate.executableFd, argv, env, out.ptr, err.ptr, release.ptr, 1)
    if (pid < 0)
        fail(
            if (xf_errno() == EACCES) ExitCode.PERMISSION else ExitCode.DEPENDENCY,
            "Cannot execute core binary",
        )
    val start = xf_millis()
    var result: Int? = null
    var exitObservedAt = 0L
    var timedOut = false
    var interrupted = false
    var killedAt: Long? = null
    val buffers = arrayOf(StringBuilder(), StringBuilder())
    val discarding = booleanArrayOf(false, false)
    val dropped = LongArray(2)
    val fds = intArrayOf(out.value, err.value)
    val open = booleanArrayOf(true, true)
    val bytes = ByteArray(4096)
    val captured = StringBuilder()
    fun emit(index: Int) {
        val line = buffers[index].map { it.code.toByte() }.toByteArray().decodeToString()
        val safe =
            redact(line, log?.secrets ?: emptySet()).take(16000) +
                (if (discarding[index])
                    " [overlong line truncated; dropped_bytes=${dropped[index]}]"
                else "")
        val lower = safe.lowercase()
        val severity =
            when {
                "[error]" in lower || "failed" in lower -> LogLevel.ERROR
                "[warning]" in lower -> LogLevel.WARNING
                "[debug]" in lower -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
        log?.event("validate", safe, if (index == 0) "CORE-OUT" else "CORE-ERR", severity)
        // System JSON helpers are not logged and must retain their complete
        // single-line document for parsing; core output remains redacted and
        // bounded by the normal capture limit.
        val capturedLine = if (log == null && maxCapturedBytes > 65536) line else safe
        if (captured.length < maxCapturedBytes) captured.append(capturedLine).append('\n')
        buffers[index].clear()
        discarding[index] = false
        dropped[index] = 0
    }
    try {
        if (gate != null) {
            gate.beforeRelease(pid)
            try {
                writeFd(release.value, byteArrayOf('X'.code.toByte()))
            } finally {
                close(release.value)
                release.value = -1
            }
        }
        while (result == null || open.any { it }) {
            val now = xf_millis()
            if (
                killedAt == null &&
                    (now - start >= timeoutMillis || (!ignoreSignals && xf_interrupted() != 0))
            ) {
                timedOut = now - start >= timeoutMillis
                interrupted = !ignoreSignals && xf_interrupted() != 0
                kill(-pid, SIGTERM)
                killedAt = now
            }
            if (killedAt != null && now - killedAt > 500) kill(-pid, SIGKILL)
            // Drain each pipe with a budget so one flood cannot starve the other or the timeout.
            for (i in 0..1) if (open[i]) {
                for (batch in 0 until 16) {
                    val n =
                        bytes.usePinned {
                            read(fds[i], it.addressOf(0), bytes.size.convert()).toInt()
                        }
                    if (n == 0) {
                        open[i] = false
                        if (buffers[i].isNotEmpty() || discarding[i]) emit(i)
                        break
                    }
                    if (n < 0) {
                        if (xf_errno() !in setOf(EAGAIN, EINTR))
                            fail(ExitCode.EXECUTION, "Core output read failed")
                        break
                    }
                    // Decode complete UTF-8 lines instead of independently decoded pipe chunks.
                    for (j in 0 until n) {
                        val c = (bytes[j].toInt() and 255).toChar()
                        if (c == '\n') emit(i)
                        else if (buffers[i].length < maxLineLength) buffers[i].append(c)
                        else {
                            discarding[i] = true
                            dropped[i]++
                        }
                    }
                }
            }
            if (result == null) {
                val status = alloc<IntVar>()
                val rc = xf_wait(pid, status.ptr)
                if (rc == pid) {
                    result = status.value
                    exitObservedAt = xf_millis()
                } else if (rc < 0 && xf_errno() != EINTR)
                    fail(ExitCode.EXECUTION, "Core wait failed")
            }
            if (result != null && now - start > timeoutMillis + 1000 && open.any { it }) {
                kill(-pid, SIGKILL)
                open.fill(false)
            }
            if (result == null || open.any { it }) usleep(1000u)
        }
    } finally {
        if (release.value >= 0) close(release.value)
        fds.forEach { close(it) }
        if (result == null) {
            kill(-pid, SIGKILL)
            val status = alloc<IntVar>()
            while (waitpid(pid, status.ptr, 0) < 0 && xf_errno() == EINTR) {}
        }
    }
    ProcessResult(
        result,
        timedOut,
        interrupted,
        captured.toString(),
        if (timedOut || interrupted) killedAt!! else exitObservedAt,
    )
}
