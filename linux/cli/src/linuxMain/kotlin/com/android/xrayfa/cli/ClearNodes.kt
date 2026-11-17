@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.serialization.json.*
import platform.posix.*

/** Explicit recovery action: lock both layouts, back up every store before clearing. */
internal fun clearNodes(paths: CliPaths = userPaths): Int {
    val directories = listOfNotNull(paths.dataDir, paths.legacyDataDir).distinct()
    return withNodeLocks(paths) {
        // Decode all stores before mutation; unsafe or corrupt inputs stay untouched.
        val stores = directories.associateWith(::loadStore)
        val nextId = stores.values.maxOf { it.getValue("nextId").jsonPrimitive.int }
        var count = 0
        for ((directory, store) in stores) {
            val path = "$directory/nodes.json"
            if (access(path, F_OK) != 0) continue
            val backup = "$directory/nodes.json.backup-${getpid()}-${xf_millis()}"
            newFile(backup, json.encodeToString(JsonObject.serializer(), store) + "\n")
            syncDirectory(directory)
            count += store.getValue("nodes").jsonArray.size
            println("Backup: $backup")
        }
        // Remove the legacy active name before publishing the canonical empty store.
        // A failure at either step still leaves a complete private backup.
        paths.legacyDataDir?.let { directory ->
            val path = "$directory/nodes.json"
            if (unlink(path) != 0 && xf_errno() != ENOENT)
                fail(ExitCode.IO, "Cannot retire legacy store; backups preserved")
            syncDirectory(directory)
        }
        val empty = JsonObject(NodeStoreCodec.empty() + ("nextId" to JsonPrimitive(nextId)))
        saveStore(empty, paths.dataDir)
        println("Cleared $count saved node records. Existing sessions are unaffected.")
        0
    }
}
