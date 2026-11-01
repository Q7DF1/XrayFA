package com.android.xrayfa.common.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CityDatabaseInstallerTest {
    private val destination = "/assets/GeoLite2-City.mmdb"
    private val partial = "$destination.download"
    private val original = byteArrayOf(1, 2, 3)
    private val replacement = byteArrayOf(4, 5, 6)
    private val files = mutableMapOf<String, ByteArray>()

    private fun installer(
        validate: (ByteArray) -> Unit = { require(it.contentEquals(replacement)) },
        download: suspend (String, String, (Float) -> Unit) -> Unit,
    ) = CityDatabaseInstaller(
        destination = destination,
        download = download,
        read = files::get,
        validate = validate,
        replace = { source, target -> files[target] = checkNotNull(files.remove(source)) },
        delete = { files.remove(it) },
    )

    @Test
    fun cancelClosesTransferAndRemovesPartialForNewAndExistingInstalls() = runBlocking {
        for (updating in listOf(false, true)) {
            files.clear()
            if (updating) files[destination] = original
            val started = CompletableDeferred<Unit>()
            var transferClosed = false
            val installer = installer { _, path, progress ->
                try {
                    files[path] = byteArrayOf(4)
                    progress(0.25f)
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    transferClosed = true
                }
            }
            val job = launch { installer.install() }
            started.await()
            assertTrue(files.containsKey(partial))
            job.cancelAndJoin()
            assertTrue(transferClosed)
            assertTrue(job.isCancelled)
            assertFalse(files.containsKey(partial))
            if (updating) assertContentEquals(original, files[destination])
            else assertFalse(files.containsKey(destination))
        }
    }

    @Test
    fun cancellationDuringValidationDoesNotCommitTheDownloadedFile() = runBlocking {
        files[destination] = original
        lateinit var job: Job
        val installer = installer(validate = { job.cancel() }) { _, path, _ -> files[path] = replacement }
        job = launch(start = CoroutineStart.LAZY) { installer.install() }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        assertContentEquals(original, files[destination])
        assertFalse(files.containsKey(partial))
    }

    @Test
    fun failedDownloadOrInvalidContentKeepsTheInstalledDatabase() = runBlocking {
        files[destination] = original
        val failed = installer { _, path, _ ->
            files[path] = byteArrayOf(4)
            error("Connection interrupted")
        }
        assertFailsWith<IllegalStateException> { failed.install() }
        assertFalse(files.containsKey(partial))
        assertContentEquals(original, files[destination])
        val invalid = installer { _, path, _ -> files[path] = byteArrayOf(7) }
        assertFailsWith<IllegalArgumentException> { invalid.install() }
        assertFalse(files.containsKey(partial))
        assertContentEquals(original, files[destination])
    }

    @Test
    fun successReplacesTheDatabaseAndLeavesNoPartialFile() = runBlocking {
        files[destination] = original
        files[partial] = byteArrayOf(9)
        installer { _, path, _ ->
            assertFalse(files.containsKey(path))
            files[path] = replacement
        }.install()
        assertContentEquals(replacement, files[destination])
        assertFalse(files.containsKey(partial))
    }

    @Test
    fun startupCleanupOnlyRemovesThePartialFile() {
        files[destination] = original
        files[partial] = byteArrayOf(4)
        installer { _, _, _ -> error("No download expected") }.clearPartialDownload()
        assertFalse(files.containsKey(partial))
        assertContentEquals(original, files[destination])
    }
}
