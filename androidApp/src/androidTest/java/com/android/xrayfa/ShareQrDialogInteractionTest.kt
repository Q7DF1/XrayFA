package com.android.xrayfa

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.shared.ui.qr.ShareQrDialog
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.android.xrayfa.utils.BarcodeUtils
import com.android.xrayfa.utils.ShareQrMatrixEncoder
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareQrDialogInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hintKeepsQrSizeAndPreviewStillCloses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.qrpreview")
        val bitmap = BarcodeUtils.createBitmap(ShareQrMatrixEncoder.encode(
            "vless://00000000-0000-0000-0000-000000000001@example.com:443?type=xdrive#Preview",
        )).asImageBitmap()
        var showHint by mutableStateOf(true)
        compose.setContent {
            V2rayForAndroidUITheme(darkTheme = false) {
                ShareQrDialog(bitmap, showHint, "clipboard export", onCopy = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Only basic VLESS", substring = true).assertIsDisplayed()
        val qr = compose.onNodeWithContentDescription("Tap to enlarge QR code")
        val withHint = qr.fetchSemanticsNode().boundsInRoot.size
        fun screenshot(name: String) {
            compose.waitForIdle()
            // Window enter/exit animations run outside Compose idling.
            android.os.SystemClock.sleep(500)
            File(context.getExternalFilesDir(null), name).outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        screenshot("share-with-hint.png")
        qr.performClick()
        compose.onNodeWithContentDescription("Share QR code").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close QR preview").performClick()
        compose.onNodeWithText("clipboard export").assertIsDisplayed()
        compose.runOnIdle { showHint = false }
        assertEquals(withHint, qr.fetchSemanticsNode().boundsInRoot.size)
        screenshot("share-without-hint.png")
    }
}
