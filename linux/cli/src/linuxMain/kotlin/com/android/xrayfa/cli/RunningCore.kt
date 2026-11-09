@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import platform.posix.*

/** Single waitpid owner. Call tick while supervising; validation timeout is separate. */
internal class RunningCore
private constructor(
    val pid: Int,
    private val pipes: IntArray,
    private var gate: Int,
    private val log: LogSession,
) {
    var exitCode: Int? = null
        private set

    private var exitedAt: Long? = null
    private var closed = false
    private val open = booleanArrayOf(true, true)
    private val pending = arrayOf(StringBuilder(), StringBuilder())
    private val dropped = LongArray(2)
    private val bytes = ByteArray(4096)

    private fun emit(i: Int) {
        val line = pending[i].map { it.code.toByte() }.toByteArray().decodeToString()
        val severity =
            when {
                "[error]" in line.lowercase() -> LogLevel.ERROR
                "[warning]" in line.lowercase() -> LogLevel.WARNING
                "[debug]" in line.lowercase() -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
        log.event(
            "runtime",
            line + (if (dropped[i] > 0) " [truncated dropped_bytes=${dropped[i]}]" else ""),
            if (i == 0) "CORE-OUT" else "CORE-ERR",
            severity,
        )
        pending[i].clear()
        dropped[i] = 0
    }

    fun release() {
        if (gate < 0) fail(ExitCode.EXECUTION, "Core gate already closed")
        try {
            writeFd(gate, byteArrayOf('X'.code.toByte()))
        } finally {
            close(gate)
            gate = -1
        }
    }

    fun tick() {
        if (closed) return
        for (i in 0..1) if (open[i]) {
            for (batch in 0 until 16) {
                val n =
                    bytes.usePinned {
                        read(pipes[i], it.addressOf(0), bytes.size.convert()).toInt()
                    }
                if (n == 0) {
                    open[i] = false
                    if (pending[i].isNotEmpty() || dropped[i] > 0) emit(i)
                    break
                }
                if (n < 0) {
                    if (xf_errno() !in setOf(EAGAIN, EINTR))
                        fail(ExitCode.EXECUTION, "Core output read failed")
                    break
                }
                for (j in 0 until n) {
                    val c = (bytes[j].toInt() and 255).toChar()
                    if (c == '\n') emit(i)
                    else if (pending[i].length < 16384) pending[i].append(c) else dropped[i]++
                }
            }
        }
        if (exitCode == null)
            memScoped {
                val status = alloc<IntVar>()
                val rc = xf_wait(pid, status.ptr)
                if (rc == pid) {
                    exitCode = status.value
                    exitedAt = xf_millis()
                } else if (rc < 0 && xf_errno() != EINTR)
                    fail(ExitCode.EXECUTION, "Cannot reap owned core")
            }
        if (exitedAt?.let { xf_millis() - it >= 2000 } == true && open.any { it }) {
            kill(-pid, SIGKILL)
            open.fill(false)
            log.event(
                "runtime",
                "Core descendants retained output; drain deadline reached",
                severity = LogLevel.WARNING,
                force = true,
            )
        }
    }

    fun stop(deadline: Long = xf_millis() + 5000): Boolean {
        if (closed) return exitCode != null
        if (gate >= 0) {
            close(gate)
            gate = -1
        }
        try {
            if (exitCode == null) kill(-pid, SIGTERM)
            val term = minOf(xf_millis() + 3000, deadline - 500)
            while (exitCode == null && xf_millis() < term) {
                tick()
                usleep(10000u)
            }
            if (exitCode == null) kill(-pid, SIGKILL)
            val end = minOf(xf_millis() + 2000, deadline)
            while ((exitCode == null || open.any { it }) && xf_millis() < end) {
                tick()
                usleep(10000u)
            }
            // Descendants still in the owned process group must also be stopped.
            kill(-pid, SIGKILL)
            return exitCode != null
        } finally {
            pipes.forEach { close(it) }
            closed = true
        }
    }

    companion object {
        fun gated(fd: Int, path: String, args: List<String>, log: LogSession): RunningCore =
            memScoped {
                fun strings(items: List<String>): CPointer<CPointerVar<ByteVar>> {
                    val array = allocArray<CPointerVar<ByteVar>>(items.size + 1)
                    items.forEachIndexed { i, s -> array[i] = s.cstr.ptr }
                    array[items.size] = null
                    return array
                }
                val out = alloc<IntVar>()
                val err = alloc<IntVar>()
                val gate = alloc<IntVar>()
                val argv = strings(listOf(path) + args)
                val env =
                    strings(
                        listOf(
                            "PATH=/usr/sbin:/usr/bin:/sbin:/bin",
                            "LANG=C",
                            "LC_ALL=C",
                            "XRAY_LOCATION_ASSET=${path.substringBeforeLast('/')}",
                        )
                    )
                val pid = xf_spawn_gated(fd, argv, env, out.ptr, err.ptr, gate.ptr, 0)
                if (pid < 0) fail(ExitCode.EXECUTION, "Cannot create gated core process")
                RunningCore(pid, intArrayOf(out.value, err.value), gate.value, log)
            }
    }
}
