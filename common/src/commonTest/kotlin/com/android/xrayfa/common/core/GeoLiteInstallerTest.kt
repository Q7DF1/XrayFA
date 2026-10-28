package com.android.xrayfa.common.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoLiteInstallerTest {
    private val destination = "/assets/GeoLite2-Country.mmdb"
    private val partial = "$destination.download"
    private val original = byteArrayOf(1, 2)
    private val replacement = byteArrayOf(3, 4)
    private val files = mutableMapOf<String, ByteArray>()
    private var installed = false

    private fun installer(download: suspend (String, String, (Float) -> Unit) -> Unit) = GeoLiteInstaller(
        destPath = destination,
        download = download,
        setInstalled = { installed = it },
        read = files::get,
        validate = { require(it.contentEquals(replacement)) },
        replace = { source, target -> files[target] = checkNotNull(files.remove(source)) },
        delete = { files.remove(it) },
    )

    @Test
    fun successValidatesAndPublishesBeforeMarkingInstalled() = runBlocking {
        files[destination] = original
        assertTrue(installer { url, path, _ ->
            assertEquals(GeoLiteAsset.DOWNLOAD_URL, url)
            assertEquals(partial, path)
            assertContentEquals(original, files[destination])
            assertFalse(installed)
            files[path] = replacement
        }.install())
        assertTrue(installed)
        assertContentEquals(replacement, files[destination])
        assertFalse(files.containsKey(partial))
    }

    @Test
    fun cancelledCountryDownloadRemovesPartialAndKeepsExistingFileAndFlag() = runBlocking {
        files[destination] = original
        installed = true
        val started = CompletableDeferred<Unit>()
        val installer = installer { _, path, _ ->
            files[path] = byteArrayOf(3)
            started.complete(Unit)
            awaitCancellation()
        }
        var returnedAsFailure = false
        val job = launch { returnedAsFailure = !installer.install() }
        started.await()
        job.cancelAndJoin()
        assertFalse(returnedAsFailure)
        assertTrue(job.isCancelled)
        assertTrue(installed)
        assertContentEquals(original, files[destination])
        assertFalse(files.containsKey(partial))
    }

    @Test
    fun failedOrInvalidDownloadLeavesInstalledFlagAndDatabaseUnchanged() = runBlocking {
        files[destination] = original
        assertFalse(installer { _, path, _ ->
            files[path] = byteArrayOf(3)
            error("network")
        }.install())
        assertFalse(installed)
        assertContentEquals(original, files[destination])
        assertFalse(files.containsKey(partial))
        assertFalse(installer { _, path, _ -> files[path] = byteArrayOf(9) }.install())
        assertFalse(installed)
        assertContentEquals(original, files[destination])
        assertFalse(files.containsKey(partial))
    }

    @Test
    fun downloadButtonRequiresVpnAndIdle() {
        assertTrue(geoLiteDownloadEnabled(vpnConnected = true, downloading = false))
        assertFalse(geoLiteDownloadEnabled(vpnConnected = false, downloading = false))
        assertFalse(geoLiteDownloadEnabled(vpnConnected = true, downloading = true))
        assertFalse(geoLiteDownloadEnabled(vpnConnected = true, downloading = false, downloadSupported = false))
    }
}
