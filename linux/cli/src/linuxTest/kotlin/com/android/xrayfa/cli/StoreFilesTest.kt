@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlin.test.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

/** Temporary files only: no CLI entry point, external process, core, or network operation. */
class StoreFilesTest {
    private fun temporary(block: (String) -> Unit) = memScoped {
        val template = "/tmp/xrayfa-store-test-XXXXXX".cstr.getPointer(this)
        val root = mkdtemp(template)?.toKString() ?: error("mkdtemp failed")
        try {
            block(root)
        } finally {
            for (name in listOf("nodes.json", "nodes.lock", "original.json")) unlink("$root/$name")
            rmdir(root)
        }
    }

    @Test
    fun safeMissingCorruptAndSymlinkStoresRemainUntouched() = temporary { root ->
        assertEquals(0, xf_store_presence(root, getuid()))
        newFile("$root/nodes.json", "not JSON")
        assertEquals(1, xf_store_presence(root, getuid()))
        assertFails { NodeStoreCodec.decode(readBytes("$root/nodes.json")!!.decodeToString()) }
        assertEquals("not JSON", readBytes("$root/nodes.json")!!.decodeToString())
        assertEquals(0, chmod("$root/nodes.json", 420u)) // 0644
        assertEquals(-1, xf_store_presence(root, getuid()))
        assertEquals(0, chmod("$root/nodes.json", 384u)) // 0600
        assertEquals(0, rename("$root/nodes.json", "$root/original.json"))
        assertEquals(0, symlink("$root/original.json", "$root/nodes.json"))
        assertEquals(-1, xf_store_presence(root, getuid()))
        assertEquals("not JSON", readBytes("$root/original.json")!!.decodeToString())
    }

    @Test
    fun snapshotAndOrdinaryDecoderUseSameSchema() = temporary { root ->
        val text =
            """{"schemaVersion":1,"nextId":2,"nodes":[{"id":1,"url":"socks://192.0.2.1:1080","protocol":"socks","address":"192.0.2.1","port":1080,"name":"fixture"}]}"""
        newFile("$root/nodes.json", text)
        val fd = xf_snapshot_open_path(root, getuid())
        assertTrue(fd >= 0)
        val bytes = ByteArray(4096)
        val length =
            try {
                bytes.usePinned { read(fd, it.addressOf(0), bytes.size.convert()).toInt() }
            } finally {
                close(fd)
            }
        assertTrue(length > 0)
        val ordinary = NodeStoreCodec.decode(readBytes("$root/nodes.json")!!.decodeToString())
        val snapshot = NodeStoreCodec.decode(bytes.copyOf(length).decodeToString())
        assertEquals(ordinary, snapshot)
        assertEquals(1, NodeStoreCodec.find(snapshot, 1)!!.getValue("id").jsonPrimitive.int)
    }
    @Test
    fun clearConflictingStoresPreservesIdsAndPrivateBackups() = temporary { root ->
        val legacy = "$root/legacy"
        privateDir(legacy)
        val text = """{"schemaVersion":1,"nextId":7,"nodes":[{"id":1,"url":"socks://192.0.2.1:1080","protocol":"socks","address":"192.0.2.1","port":1080,"name":"fixture"}]}"""
        newFile("$root/nodes.json", text)
        newFile("$legacy/nodes.json", text)
        val paths = CliPaths(root, root, root, legacy, null, getuid())
        assertFails { selectedStoreDirectory(paths) }
        try {
            assertEquals(0, clearNodes(paths))
            assertEquals(root, selectedStoreDirectory(paths))
            val store = loadStore(root)
            assertTrue(store.getValue("nodes").jsonArray.isEmpty())
            assertEquals(7, store.getValue("nextId").jsonPrimitive.int)
            assertEquals(0, xf_store_presence(legacy, getuid()))
            for (directory in listOf(root, legacy)) {
                val handle = opendir(directory) ?: error("opendir")
                var backups = 0
                try {
                    while (true) {
                        val entry = readdir(handle) ?: break
                        val name = entry.pointed.d_name.toKString()
                        if (name.startsWith("nodes.json.backup-")) {
                            val path = "$directory/$name"
                            assertEquals(1, xf_owned(path, 0))
                            assertEquals(NodeStoreCodec.decode(text), NodeStoreCodec.decode(readBytes(path)!!.decodeToString()))
                            unlink(path)
                            backups++
                        }
                    }
                } finally { closedir(handle) }
                assertEquals(1, backups)
            }
        } finally {
            unlink("$legacy/nodes.json")
            unlink("$legacy/nodes.lock")
            rmdir(legacy)
        }
    }

}
