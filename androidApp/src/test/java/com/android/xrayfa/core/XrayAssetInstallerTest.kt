package com.android.xrayfa.core

import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.core.XrayAssetPaths
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XrayAssetInstallerTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun paths() = object : XrayAssetPaths {
        override val basePath = temporary.root.absolutePath
        override val geoIpPath = File(temporary.root, GEO_IP).absolutePath
        override val geoSitePath = File(temporary.root, GEO_SITE).absolutePath
        override val geoLiteDatabasePath = File(temporary.root, "GeoLite2-Country.mmdb").absolutePath
    }

    private fun dataset(code: String, ip: Boolean = false): ByteArray {
        val key = code.toByteArray()
        val value = if (ip) byteArrayOf(10, 4, 192.toByte(), 0, 2, 0, 16, 24)
            else byteArrayOf(8, 2, 18, 11) + "example.com".toByteArray()
        val body = byteArrayOf(10, key.size.toByte()) + key + byteArrayOf(18, value.size.toByte()) + value
        return byteArrayOf(10, body.size.toByte()) + body
    }

    private fun installer(open: (String) -> InputStream = {
        ByteArrayInputStream(if (it == GEO_IP) dataset("CN", true) else dataset("TELEGRAM"))
    }) = XrayAssetInstaller(paths(), open)

    @Test fun installsMissingAssetsAndPublishesOnlyCompleteFiles() {
        installer().ensureInstalled()
        assertEquals(setOf("CN"), XrayAssetInstaller.readCodes(File(paths().geoIpPath)))
        assertEquals(setOf("TELEGRAM"), XrayAssetInstaller.readCodes(File(paths().geoSitePath)))
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "tmp" })
    }

    @Test fun restoresTruncatedCacheAndPreservesBackup() {
        val original = dataset("TELEGRAM").dropLast(3).toByteArray()
        val cache = File(paths().geoSitePath).apply { writeBytes(original) }
        assertNull(XrayAssetInstaller.readCodes(cache))
        installer().ensureInstalled()
        assertEquals(setOf("TELEGRAM"), XrayAssetInstaller.readCodes(cache))
        val backup = temporary.root.listFiles()!!.single { it.name.startsWith("$GEO_SITE.corrupt-") }
        assertArrayEquals(original, backup.readBytes())
    }

    @Test fun preservesValidCustomDatasetAndDoesNotOpenBundledAsset() {
        File(paths().geoIpPath).writeBytes(dataset("CUSTOM-IP", true))
        val custom = dataset("CUSTOM-SITE")
        File(paths().geoSitePath).writeBytes(custom)
        installer { error("Valid custom geodata must be preserved") }.ensureInstalled()
        assertArrayEquals(custom, File(paths().geoSitePath).readBytes())
    }

    @Test fun explicitRestoreReplacesValidCustomDataAndKeepsBackup() {
        File(paths().geoIpPath).writeBytes(dataset("CUSTOM-IP", true))
        val custom = dataset("CUSTOM-SITE")
        File(paths().geoSitePath).writeBytes(custom)
        installer().ensureInstalled(forceRestore = true)
        assertEquals(setOf("TELEGRAM"), XrayAssetInstaller.readCodes(File(paths().geoSitePath)))
        val backup = temporary.root.listFiles()!!.single { it.name.startsWith("$GEO_SITE.backup-") }
        assertArrayEquals(custom, backup.readBytes())
    }

    @Test fun failedCopyDoesNotLeaveAnExistingPartialFile() {
        val brokenSource = object : InputStream() {
            var count = 0
            override fun read(): Int = if (count++ < 4) 10 else throw IOException("Interrupted copy")
        }
        try {
            installer { brokenSource }.ensureInstalled()
            fail("Expected copy failure")
        } catch (_: IOException) { }
        assertFalse(File(paths().geoIpPath).exists())
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "tmp" })
    }

    @Test fun emptyAndTruncatedDownloadDoNotReplaceWorkingDataset() {
        val target = File(paths().geoSitePath)
        val valid = dataset("TELEGRAM")
        target.writeBytes(valid)
        for (bad in listOf(byteArrayOf(), valid.dropLast(1).toByteArray(), "<html>error</html>".toByteArray())) {
            val download = temporary.newFile().apply { writeBytes(bad) }
            try {
                XrayAssetInstaller.publish(download, target)
                fail("Expected invalid geodata rejection")
            } catch (_: IllegalStateException) { }
            assertArrayEquals(valid, target.readBytes())
        }
    }

    @Test fun completeReplacementIsPublishedAtomically() {
        val target = File(paths().geoSitePath).apply { writeBytes(dataset("OLD")) }
        val download = temporary.newFile().apply { writeBytes(dataset("NEW")) }
        XrayAssetInstaller.publish(download, target)
        assertEquals(setOf("NEW"), XrayAssetInstaller.readCodes(target))
        assertFalse(download.exists())
    }
}
