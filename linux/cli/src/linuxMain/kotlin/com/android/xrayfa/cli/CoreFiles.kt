@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import platform.posix.*

internal fun hashFile(path: String, follow: Boolean = false): String {
    // Core is larger than node/config files. Streaming avoids loading it into memory.
    val fd = open(path, O_RDONLY or (if (follow) 0 else O_NOFOLLOW) or O_CLOEXEC)
    if (fd < 0) fail(ExitCode.DEPENDENCY, "Cannot read executable for identity hash")
    try {
        // Public API provides a streaming digest factory on Linux.
        val digest = com.android.xrayfa.common.utils.LinuxDigest.create("SHA-256")
        val b = ByteArray(65536)
        while (true) {
            val n = b.usePinned { read(fd, it.addressOf(0), b.size.convert()).toInt() }
            if (n == 0) break
            if (n < 0) {
                if (xf_errno() == EINTR) continue
                fail(ExitCode.DEPENDENCY, "Cannot hash core")
            }
            digest.update(b, 0, n)
        }
        return digest.finalize().joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
    } finally {
        close(fd)
    }
}

internal fun canonicalCore(path: String): String = memScoped {
    val b = allocArray<ByteVar>(4096)
    realpath(absolute(path), b)?.toKString()
        ?: fail(ExitCode.DEPENDENCY, "Core path does not exist")
}
