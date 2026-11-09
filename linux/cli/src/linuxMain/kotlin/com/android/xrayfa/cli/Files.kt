@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

internal val json = Json { prettyPrint = true }

internal fun absolute(path: String): String {
    if (path.startsWith('/')) return path
    val cwd =
        memScoped {
            val b = allocArray<ByteVar>(4096)
            getcwd(b, 4096u)?.toKString()
        } ?: fail(ExitCode.IO, "Cannot resolve current directory")
    return "$cwd/$path"
}

internal val userPaths: CliPaths
    get() {
        val ownerHome = xf_home()?.toKString() ?: fail(ExitCode.IO, "Cannot resolve user home")
        return callerPaths(getuid(), ownerHome)
    }
internal val dataDir
    get() = selectedStoreDirectory(userPaths)
internal val logDir
    get(): String {
        val paths = userPaths
        val legacy = paths.legacyLogDir ?: return paths.logDir
        if (access(paths.logDir, F_OK) == 0) return paths.logDir
        if (access(legacy, F_OK) != 0) {
            if (xf_errno() != ENOENT) fail(ExitCode.LOG, "Cannot inspect legacy logs")
            return paths.logDir
        }
        if (xf_owned(legacy, 1) != 1) fail(ExitCode.LOG, "Unsafe legacy log directory")
        return legacy
    }

internal fun privateDir(path: String) {
    // Shared parent directories are allowed; the application leaf must be private and owned.
    val parent = path.substringBeforeLast('/', "")
    if (parent.isNotEmpty() && access(parent, F_OK) != 0) privateDir(parent)
    if (mkdir(path, 448u) != 0 && xf_errno() != EEXIST)
        fail(ExitCode.IO, "Cannot create private directory")
    if (xf_owned(path, 1) != 1)
        fail(ExitCode.IO, "Unsafe directory ownership, permissions or symbolic link")
}

internal fun ownedFile(path: String, missing: Boolean = false) {
    val state = xf_owned(path, 0)
    if (state < 0 || (!missing && state == 0)) fail(ExitCode.IO, "Unsafe or missing private file")
}

internal fun readBytes(
    path: String,
    max: Int = 16 * 1024 * 1024,
    missing: Boolean = false,
): ByteArray? {
    val fd = open(path, O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
    if (fd < 0) {
        if (missing && xf_errno() == ENOENT) return null
        fail(ExitCode.IO, "Cannot read file")
    }
    try {
        val parts = ArrayList<ByteArray>()
        var total = 0
        val b = ByteArray(8192)
        while (true) {
            val n = b.usePinned { read(fd, it.addressOf(0), b.size.convert()).toInt() }
            if (n == 0) break
            if (n < 0) {
                if (xf_errno() == EINTR) continue
                fail(ExitCode.IO, "File read failed")
            }
            total += n
            if (total > max) fail(ExitCode.IO, "File exceeds size limit")
            parts.add(b.copyOf(n))
        }
        return ByteArray(total).also { out ->
            var pos = 0
            for (p in parts) {
                p.copyInto(out, pos)
                pos += p.size
            }
        }
    } finally {
        close(fd)
    }
}

internal fun writeFd(fd: Int, data: ByteArray) {
    var pos = 0
    while (pos < data.size) {
        val n = data.usePinned { write(fd, it.addressOf(pos), (data.size - pos).convert()).toInt() }
        if (n < 0 && xf_errno() == EINTR) continue
        if (n <= 0) fail(ExitCode.IO, "File write failed")
        pos += n
    }
}

internal fun newFile(path: String, text: String) {
    val fd = open(path, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
    if (fd < 0)
        fail(
            if (xf_errno() == EEXIST) ExitCode.CONFLICT
            else if (xf_errno() == EACCES) ExitCode.PERMISSION else ExitCode.IO,
            "Cannot create output file (existing outputs are preserved)",
        )
    try {
        writeFd(fd, text.encodeToByteArray())
        if (fsync(fd) != 0) fail(ExitCode.IO, "File sync failed")
    } finally {
        close(fd)
    }
}

internal fun <T> storeLock(block: (String) -> T): T {
    val directory = dataDir
    privateDir(directory)
    val path = "$directory/nodes.lock"
    val fd = open(path, O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC, 384)
    if (fd < 0) fail(ExitCode.IO, "Cannot open node lock")
    try {
        ownedFile(path)
        if (xf_lock(fd) != 0) fail(ExitCode.IO, "Cannot lock nodes")
        return block(directory)
    } finally {
        close(fd)
    }
}

internal fun loadStore(directory: String): JsonObject {
    val path = "$directory/nodes.json"
    ownedFile(path, missing = true)
    val text = readBytes(path, missing = true)?.decodeToString() ?: return NodeStoreCodec.empty()
    return try {
        NodeStoreCodec.decode(text)
    } catch (_: Exception) {
        fail(ExitCode.IO, "Node file is corrupt or has an unsupported schema; original preserved")
    }
}

internal fun saveStore(store: JsonObject, directory: String) {
    val temp = "$directory/.nodes-${getpid()}-${xf_millis()}.tmp"
    try {
        newFile(temp, json.encodeToString(JsonObject.serializer(), store))
        if (rename(temp, "$directory/nodes.json") != 0)
            fail(ExitCode.IO, "Atomic node replacement failed")
        val dir = open(directory, O_RDONLY or O_DIRECTORY or O_CLOEXEC)
        if (dir < 0) fail(ExitCode.IO, "Cannot open node directory")
        try {
            if (fsync(dir) != 0) fail(ExitCode.IO, "Node directory sync failed")
        } finally {
            close(dir)
        }
    } finally {
        unlink(temp)
    }
}

internal fun utc(): String = memScoped {
    val now = alloc<time_tVar>()
    time(now.ptr)
    val tm = alloc<tm>()
    gmtime_r(now.ptr, tm.ptr)
    val b = allocArray<ByteVar>(32)
    strftime(b, 32u, "%Y-%m-%dT%H:%M:%SZ", tm.ptr)
    b.toKString()
}

internal fun clean(text: String): String =
    text.map { if (it.code < 32 || it.code == 127) ' ' else it }.joinToString("")
