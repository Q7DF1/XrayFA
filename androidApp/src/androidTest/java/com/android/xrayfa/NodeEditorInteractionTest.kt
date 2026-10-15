package com.android.xrayfa

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

/** Run with APPLICATION_ID=com.android.xrayfa.nodetest to isolate stored test data. */
@RunWith(AndroidJUnit4::class)
class NodeEditorInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var submitted: NodeEditForm? = null
    private val editor get() = KoinPlatform.getKoin().get<NodeFormEditor>()

    @Before
    fun requireIsolatedPackage() {
        assumeTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "com.android.xrayfa.nodetest")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openEditor() {
        compose.setContent {
            MaterialTheme {
                SharedEditScreen(0, null, null, null, editor, onBack = {}, onSave = { submitted = it })
            }
        }
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
    private fun fill(label: String, text: String) {
        field(label).performScrollTo().performTextReplacement(text)
    }
    private fun picker(label: String, value: String) = compose.onNodeWithContentDescription("$label: $value")

    @Test
    fun entireDropdownOpensAndSecurityOptionsFollowTransport() {
        openEditor()
        picker("Network", "raw").performScrollTo().performClick()
        screenshot("node-editor-network-options.png")
        compose.onNodeWithText("ws", useUnmergedTree = true).performClick()
        picker("Network", "ws").assertExists()
        picker("Security", "tls").performScrollTo().performClick()
        compose.onNodeWithText("reality", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("tls", useUnmergedTree = true).performClick()
        field("Path").performScrollTo().assertExists()
        field("Host").assertExists()
    }

    @Test
    fun invalidInputIsRejectedAndProtocolDraftsAreRetained() {
        openEditor()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.onNodeWithText("Address: Required").assertExists()
        assertNull(submitted)
        fill("Address", "example.com")
        fill("Port (1-65535)", "443")
        fill("UUID", TEST_ID)
        compose.onNodeWithText("trojan", useUnmergedTree = true).performScrollTo().performClick()
        field("Password").performScrollTo().assertExists()
        compose.onNodeWithText("vless", useUnmergedTree = true).performScrollTo().performClick()
        field("UUID").performScrollTo().assertTextContains(TEST_ID)
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle {
            assertEquals(TEST_ID, submitted?.uuidOrPassword)
            assertEquals(Protocol.VLESS, submitted?.selectedProtocol)
            assertEquals("tls", submitted?.transportSecurity)
        }
    }

    @Test
    fun protocolTabsShowOnlyApplicableFields() {
        openEditor()
        for ((protocol, credential) in listOf(
            "vmess" to "UUID", "shadowsocks" to "Password", "trojan" to "Password",
            "hysteria2" to "Auth", "socks" to "Username (optional)", "http" to "Username (optional)",
        )) {
            compose.onNodeWithText(protocol, useUnmergedTree = true).performScrollTo().performClick()
            field(credential).performScrollTo().assertIsDisplayed()
            if (protocol in setOf("shadowsocks", "hysteria2", "socks", "http")) {
                compose.onNodeWithContentDescription("Network: raw").assertDoesNotExist()
            }
        }
    }

    @Test
    fun previewAndSaveProducePersistedEditableNode() {
        openEditor()
        val remark = "Node editor instrumented test ${System.nanoTime()}"
        fill("Remarks", remark)
        fill("Address", "2001:db8::1")
        fill("Port (1-65535)", "443")
        fill("UUID", TEST_ID)
        compose.onNodeWithText("Preview outbound JSON").performScrollTo().performClick()
        compose.onNode(hasText("\"protocol\": \"vless\"", substring = true)).assertExists()
        compose.onNode(hasText("\"address\": \"2001:db8::1\"", substring = true)).assertExists()
        screenshot("node-editor-outbound-preview.png")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Save").assertIsDisplayed().performClick()
        compose.runOnIdle { assertNotNull(submitted) }
        runBlocking {
            val repository = KoinPlatform.getKoin().get<NodeRepository>()
            assertTrue(editor.saveForm(0, requireNotNull(submitted)))
            val saved = repository.allNodes.first().single { it.remark == remark }
            try {
                assertEquals("2001:db8::1", saved.address)
                assertEquals(443, saved.port)
                val reopened = editor.parseForm(saved.protocolPrefix, saved.url, saved.remark)
                assertEquals(TEST_ID, reopened.uuidOrPassword)
                assertEquals("tls", reopened.transportSecurity)
            } finally {
                repository.deleteLink(saved)
            }
        }
    }

    private companion object {
        const val TEST_ID = "00000000-0000-0000-0000-000000000001"
    }
}
