package com.android.xrayfa.common.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeoLiteInstallerTest {

    @Test
    fun success_downloadsOfficialUrlToDestAndMarksInstalled() = runTestBlocking {
        var downloadedUrl: String? = null
        var downloadedDest: String? = null
        var installed: Boolean? = null
        val installer =
            GeoLiteInstaller(
                destPath = "/tmp/GeoLite2-Country.mmdb",
                download = { url, dest, _ ->
                    downloadedUrl = url
                    downloadedDest = dest
                },
                setInstalled = { installed = it },
            )

        assertTrue(installer.install())
        assertEquals(GeoLiteAsset.DOWNLOAD_URL, downloadedUrl)
        assertEquals("/tmp/GeoLite2-Country.mmdb", downloadedDest)
        assertEquals(true, installed)
    }

    @Test
    fun failure_doesNotMarkInstalled() = runTestBlocking {
        var installed: Boolean? = null
        val installer =
            GeoLiteInstaller(
                destPath = "/tmp/GeoLite2-Country.mmdb",
                download = { _, _, _ -> error("network") },
                setInstalled = { installed = it },
            )

        assertFalse(installer.install())
        assertEquals(null, installed)
    }

    @Test
    fun downloadButton_requiresVpnAndIdle() {
        assertTrue(geoLiteDownloadEnabled(vpnConnected = true, downloading = false))
        assertFalse(geoLiteDownloadEnabled(vpnConnected = false, downloading = false))
        assertFalse(geoLiteDownloadEnabled(vpnConnected = true, downloading = true))
    }

    @Test
    fun downloadButton_disabledWhenPlatformDoesNotSupportDownload() {
        assertFalse(
            geoLiteDownloadEnabled(
                vpnConnected = true,
                downloading = false,
                downloadSupported = false,
            ),
        )
    }
}

private fun runTestBlocking(block: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
