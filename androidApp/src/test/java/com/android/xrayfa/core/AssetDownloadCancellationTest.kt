package com.android.xrayfa.core

import com.android.xrayfa.common.core.AtomicAssetInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class AssetDownloadCancellationTest {
    @Test
    fun cancellationClosesTheFileAndDeletesPartialsWithoutReplacingInstalledAssets() = runBlocking {
        val directory = Files.createTempDirectory("xrayfa-download-cancel").toFile()
        try {
            for (name in listOf("geoip.dat", "geosite.dat", "GeoLite2-Country.mmdb")) {
                val target = File(directory, name)
                val partial = File(directory, "$name.download")
                val original = byteArrayOf(1, 2, 3)
                target.writeBytes(original)
                partial.writeBytes(byteArrayOf(9))
                val started = CompletableDeferred<Unit>()
                var stream: FileOutputStream? = null
                val installer = AtomicAssetInstaller(
                    destination = target.path,
                    url = "https://example.test/$name",
                    download = { _, path, progress ->
                        File(path).outputStream().also { stream = it }.use {
                            it.write(byteArrayOf(4))
                            progress(0.25f)
                            started.complete(Unit)
                            awaitCancellation()
                        }
                    },
                    validate = { error("A cancelled download must not be installed") },
                )
                val job = launch { installer.install() }
                started.await()
                job.cancelAndJoin()
                assertNotNull(stream)
                assertFalse(stream!!.fd.valid())
                assertFalse(partial.exists())
                assertArrayEquals(original, target.readBytes())
            }
        } finally {
            directory.deleteRecursively()
        }
        Unit
    }
}
