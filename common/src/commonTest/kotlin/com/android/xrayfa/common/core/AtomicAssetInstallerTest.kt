package com.android.xrayfa.common.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicAssetInstallerTest {
    @Test
    fun allGeodataTypesCanBeCancelledWithoutPublishingOrLeavingPartials() = runBlocking {
        for (name in listOf("geoip.dat", "geosite.dat", "GeoLite2-Country.mmdb")) {
            val destination = "/assets/$name"
            val partial = "$destination.download"
            val old = byteArrayOf(1, 2)
            val files = mutableMapOf(destination to old, partial to byteArrayOf(9))
            val started = CompletableDeferred<Unit>()
            var streamClosed = false
            var validated = false
            var published = false
            val installer = AtomicAssetInstaller(
                destination = destination,
                url = "https://example.test/$name",
                download = { _, path, progress ->
                    assertFalse(files.containsKey(path))
                    try {
                        files[path] = byteArrayOf(3)
                        progress(0.3f)
                        started.complete(Unit)
                        awaitCancellation()
                    } finally { streamClosed = true }
                },
                validate = { validated = true },
                replace = { _, _ -> published = true },
                delete = { path ->
                    if (streamClosed) assertTrue(files.containsKey(destination))
                    files.remove(path)
                },
            )
            val job = launch { installer.install() }
            started.await()
            job.cancelAndJoin()
            assertTrue(streamClosed)
            assertFalse(validated)
            assertFalse(published)
            assertContentEquals(old, files[destination])
            assertFalse(files.containsKey(partial))
        }
    }
}
