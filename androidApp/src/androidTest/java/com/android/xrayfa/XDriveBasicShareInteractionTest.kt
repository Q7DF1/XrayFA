package com.android.xrayfa

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.config.XDriveForm
import com.android.xrayfa.model.*
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.config.*
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.ui.AndroidPlatformRootHooks
import com.android.xrayfa.viewmodel.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

@RunWith(AndroidJUnit4::class)
class XDriveBasicShareInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun qrClipboardImportAndCompletionShareOnlyBasicInformation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.xdrivetest")
        val koin = KoinPlatform.getKoin()
        val repository = koin.get<NodeRepository>()
        val editor = koin.get<NodeFormEditor>()
        val remark = "basic-share-${System.nanoTime()}"
        val source = runBlocking {
            assertTrue(editor.saveForm(0, NodeEditForm(remarks = remark, address = "127.0.0.1", port = "443", network = "xdrive", transportSecurity = "none",
                uuidOrPassword = "00000000-0000-0000-0000-000000000001",
                vlessEncryption = "mlkem768x25519plus.native.0rtt.${"A".repeat(43)}",
                xdrive = XDriveForm(service = "google-drive", remoteFolder = "private-folder-id",
                    secrets = listOf("private-client-id", "private-client-secret", "private-refresh-token")))))
            repository.allNodes.first().single { it.remark == remark }
        }
        var draft: Node? by mutableStateOf(null)
        var shareVisible by mutableStateOf(true)
        var submitted: NodeEditForm? = null
        lateinit var vm: XrayViewmodel
        compose.runOnUiThread {
            vm = ViewModelProvider(compose.activity, koin.get<XrayViewmodelFactory>())[XrayViewmodel::class.java]
        }
        try {
            compose.setContent {
                val settings = ViewModelProvider(compose.activity, koin.get<SettingsViewmodelFactory>())[SettingsViewmodel::class.java]
                val apps = ViewModelProvider(compose.activity, koin.get<AppsViewmodelFactory>())[AppsViewmodel::class.java]
                MaterialTheme {
                    val editing = draft
                    if (editing != null) {
                        SharedEditScreen(editing.id, editing.protocolPrefix, editing.jsonData, editing.remark,
                            editor, onBack = {}, onSave = { submitted = it })
                    } else if (shareVisible) {
                        AndroidPlatformRootHooks(settings, apps, vm).ShareNode(source) { shareVisible = false }
                    }
                }
            }
            compose.waitUntil(10_000) { vm.qrBitmap.value != null }
            val bitmap = requireNotNull(vm.qrBitmap.value)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val qrText = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
            assertEquals(source.configurationShareLink(), qrText)
            assertFalse(qrText.contains("private"))
            compose.onNodeWithText(context.getString(R.string.clipboard_export)).performClick()
            compose.waitUntil(10_000) { !shareVisible }
            compose.runOnIdle {
                val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals(qrText, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            }
            val imported = runBlocking {
                koin.get<ConfigLinkImporter>().addLink(qrText)
                repository.allNodes.first().single { it.remark == remark && it.id != source.id }
            }
            assertTrue(imported.requiresXDriveSetup)
            assertFalse(imported.selected)
            compose.runOnIdle { draft = imported }
            compose.onNodeWithContentDescription("Network: xdrive").performScrollTo().assertExists()
            compose.onNode(hasSetTextAction() and hasText("UUID")).performScrollTo()
                .assertTextContains("00000000-0000-0000-0000-000000000001")
            compose.onNodeWithContentDescription("Storage backend: google-drive").performScrollTo().performClick()
            compose.onNodeWithText("local", useUnmergedTree = true).performClick()
            compose.onNode(hasSetTextAction() and hasText("Storage folder")).performScrollTo()
                .performTextReplacement("/data/local/tmp/basic-share-test")
            compose.onNode(hasSetTextAction() and hasText("Address")).performScrollTo()
                .performTextReplacement("192.0.2.10")
            compose.onNode(hasSetTextAction() and hasText("Port (1-65535)")).performScrollTo()
                .performTextReplacement("9000")
            compose.onNodeWithContentDescription("Save").performClick()
            compose.runOnIdle { assertNotNull(submitted) }
            runBlocking {
                assertTrue(editor.saveForm(imported.id, requireNotNull(submitted)))
                val complete = requireNotNull(repository.loadLinksById(imported.id).first())
                assertFalse(complete.requiresXDriveSetup)
                complete.requireXDriveReady()
                val changedLink = requireNotNull(complete.configurationShareLink())
                assertTrue(changedLink.contains("@192.0.2.10:9000?"))
                val reopened = editor.parseForm(complete.protocolPrefix, complete.jsonData, complete.remark)
                assertEquals("192.0.2.10", reopened.address)
                assertEquals("9000", reopened.port)
            }
        } finally {
            runBlocking { repository.allNodes.first().filter { it.remark == remark }.forEach { repository.deleteLink(it) } }
        }
    }
}
