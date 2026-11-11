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
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.canShareConfiguration
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.model.isManagedXDrive
import com.android.xrayfa.model.JSON_CONFIG_PREFIX
import com.android.xrayfa.model.jsonConfigIdentifier
import com.android.xrayfa.config.XDriveForm
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.JsonConfigEditor
import com.android.xrayfa.shared.ui.RootContent
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.createRootComponent
import com.android.xrayfa.shared.ui.config.JsonConfigEditScreen
import com.android.xrayfa.shared.navigation.defaultConfigComponentFactory
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.android.xrayfa.ui.AndroidPlatformRootHooks
import com.android.xrayfa.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

/** Real Room suspension and dialog disposal must not cancel an ordinary clipboard export. */
@RunWith(AndroidJUnit4::class)
class XDriveSharingInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualRoutesReopenManagedVlessAndKeepGlobalOrMalformedJsonGeneric() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.xdrivetest")
        val koin = KoinPlatform.getKoin()
        val repository = koin.get<NodeRepository>()
        val editor = koin.get<NodeFormEditor>()
        val prefix = "xdrive-routes-${System.nanoTime()}"
        val lifecycle = LifecycleRegistry()
        val source = runBlocking {
            assertTrue(editor.saveForm(0, NodeEditForm(remarks = "$prefix-managed", network = "xdrive",
                uuidOrPassword = "00000000-0000-0000-0000-000000000001",
                vlessEncryption = "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}",
                xdrive = XDriveForm(service = "local", remoteFolder = "/data/local/tmp/xdrive-fixture"))))
            repository.allNodes.first().single { it.remark == "$prefix-managed" }
        }
        try {
            runBlocking {
                JsonConfigEditor(repository).save(0, "$prefix-global", requireNotNull(source.jsonData), null)
                repository.addNode(Node(protocolPrefix = JSON_CONFIG_PREFIX, address = "", port = 0, subscriptionId = -1,
                    remark = "$prefix-malformed", url = jsonConfigIdentifier("xdrive-malformed-$prefix", null),
                    jsonData = """{"outbounds":[{"protocol":"freedom"}]}"""))
            }
            val records = runBlocking { repository.allNodes.first().filter { it.remark?.startsWith(prefix) == true } }
            val global = records.single { it.remark == "$prefix-global" }
            val malformed = records.single { it.remark == "$prefix-malformed" }
            assertFalse(global.isManagedXDrive)
            lateinit var root: RootComponent
            compose.runOnUiThread { lifecycle.resume(); root = createRootComponent(lifecycle); root.selectTab(RootTab.Config) }
            compose.setContent { MaterialTheme { RootContent(root) } }
            compose.waitUntil(10_000) {
                (root.pages.value.items.first { it.configuration == RootTab.Config }.instance as? RootComponent.Child.Config)
                    ?.component?.nodeById(source.id) != null
            }
            compose.runOnUiThread { root.openNodeEdit(source.id) }
            compose.onNodeWithContentDescription("Network: xdrive").performScrollTo().assertExists()
            compose.onNodeWithText("Import JSON file").assertDoesNotExist()
            compose.runOnUiThread { root.navigateBack(); root.openJsonConfigEdit(global.id) }
            compose.onNode(hasSetTextAction() and hasText("Xray JSON configuration")).assertExists()
            compose.onNodeWithContentDescription("Storage backend: local").assertDoesNotExist()
            compose.runOnUiThread { root.navigateBack(); root.openNodeEdit(malformed.id) }
            compose.onNode(hasSetTextAction() and hasText("Xray JSON configuration")).assertExists()
            compose.runOnUiThread { root.navigateBack(); root.openNodeEdit(0) }
            compose.onNodeWithContentDescription("Network: raw").performScrollTo().performClick()
            compose.onNodeWithText("xdrive", useUnmergedTree = true).performClick()
            val count = runBlocking { repository.allNodes.first().size }
            compose.onNodeWithContentDescription("Save").performClick()
            compose.onNode(hasText("Invalid value or configuration", substring = true)).assertExists()
            compose.onNodeWithContentDescription("Network: xdrive").assertExists()
            assertEquals(count, runBlocking { repository.allNodes.first().size })
        } finally {
            compose.runOnUiThread { lifecycle.destroy() }
            runBlocking { repository.allNodes.first().filter { it.remark?.startsWith(prefix) == true }.forEach { repository.deleteLink(it) } }
        }
    }

    @Test fun roomConversionPreservesMetadataAndRemovesCloudCredentialsWhenLeavingXdrive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.xdrivetest")
        val repository = KoinPlatform.getKoin().get<NodeRepository>()
        val editor = KoinPlatform.getKoin().get<NodeFormEditor>()
        val remark = "xdrive-room-${System.nanoTime()}"
        val original = runBlocking {
            repository.addNode(Node(protocolPrefix = "vless", address = "127.0.0.1", port = 443, subscriptionId = 12345,
                selected = true, favorite = true, isPreNode = true, isNextNode = true, remark = remark,
                url = "vless://00000000-0000-0000-0000-000000000001@127.0.0.1:443?security=tls"))
            repository.allNodes.first().single { it.remark == remark }
        }
        val secret = "fixture-cloud-credential"
        try {
            val form = editor.parseForm("vless", original.url, remark).copy(network = "xdrive",
                vlessEncryption = "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}",
                xdrive = XDriveForm(service = "google-drive", remoteFolder = "fixture-folder", secrets = listOf(secret, secret, secret)))
            assertTrue(runBlocking { editor.saveForm(original.id, form) })
            val local = runBlocking { repository.loadLinksById(original.id).first() }!!
            assertTrue(local.isManagedXDrive && local.selected && local.favorite && local.isPreNode && local.isNextNode)
            assertEquals(original.subscriptionId, local.subscriptionId)
            assertTrue(local.jsonData!!.contains(secret))
            val ordinary = editor.parseForm(local.protocolPrefix, local.jsonData, remark).copy(network = "raw", address = "127.0.0.1", port = "443")
            assertTrue(runBlocking { editor.saveForm(local.id, ordinary) })
            val updated = runBlocking { repository.loadLinksById(original.id).first() }!!
            assertEquals(original.id, updated.id)
            assertEquals(original.subscriptionId, updated.subscriptionId)
            assertTrue(updated.selected && updated.favorite && updated.isPreNode && updated.isNextNode)
            assertNull(updated.jsonData)
            assertFalse(updated.url.contains(secret))
        } finally {
            runBlocking { repository.deleteLinkById(original.id) }
        }
    }

    @Test fun globalJsonImportDoesNotBecomeAnXdriveEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.xdrivetest")
        val lifecycle = LifecycleRegistry()
        try {
            lateinit var component: com.android.xrayfa.shared.navigation.ConfigComponent
            compose.runOnUiThread { component = defaultConfigComponentFactory()(DefaultComponentContext(lifecycle)) }
            compose.setContent { MaterialTheme { JsonConfigEditScreen(0, component, onBack = {}) } }
            val source = XDriveForm(id = "00000000-0000-0000-0000-000000000001",
                encryption = "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}", service = "local",
                remoteFolder = "/data/local/tmp/xdrive-fixture").build().toString()
            compose.onNode(hasSetTextAction() and hasText("Xray JSON configuration")).performTextReplacement(source)
            compose.onNodeWithText("Create XDRIVE configuration").assertDoesNotExist()
            compose.onNodeWithContentDescription("Storage backend: local").assertDoesNotExist()
            compose.onNodeWithText("JSON configuration").assertExists()
        } finally {
            compose.runOnUiThread { lifecycle.destroy() }
        }
    }

    @Test fun vlessNetworkDropdownConfiguresXdriveInlineAndReopensTheSameEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.xdrivetest")
        val repository = KoinPlatform.getKoin().get<NodeRepository>()
        val editor = KoinPlatform.getKoin().get<NodeFormEditor>()
        val remark = "xdrive-inline-${System.nanoTime()}"
        var submitted: com.android.xrayfa.shared.config.NodeEditForm? = null
        try {
            compose.setContent {
                MaterialTheme {
                    SharedEditScreen(0, null, null, null, editor, onBack = {}, onSave = { submitted = it })
                }
            }
            compose.onNodeWithText("Create XDRIVE configuration").assertDoesNotExist()
            compose.onNodeWithContentDescription("Network: raw").performScrollTo().performClick()
            compose.onNodeWithText("xdrive", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("Network: xdrive").assertExists()
            compose.onNodeWithText("Import JSON file").assertDoesNotExist()
            compose.onNodeWithText("Preview outbound JSON").assertDoesNotExist()
            fun fill(label: String, value: String) = compose.onNode(hasSetTextAction() and hasText(label))
                .performScrollTo().performTextReplacement(value)
            fill("Remarks", remark)
            fill("UUID", "00000000-0000-0000-0000-000000000001")
            fill("VLESS Encryption (required)", "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}")
            compose.onNodeWithContentDescription("Storage backend: google-drive").performScrollTo().performClick()
            compose.onNodeWithText("local", useUnmergedTree = true).performClick()
            fill("Storage folder", "/data/local/tmp/xdrive-fixture")
            compose.onNodeWithContentDescription("Save").performClick()
            compose.runOnIdle { assertNotNull(submitted) }
            assertTrue(runBlocking { editor.saveForm(0, requireNotNull(submitted)) })
            val node = runBlocking { repository.allNodes.first().single { it.remark == remark } }
            assertTrue(node.isManagedXDrive)
            assertFalse(node.canShareConfiguration)
            val reopened = editor.parseForm(node.protocolPrefix, node.jsonData, node.remark)
            assertEquals("xdrive", reopened.network)
            assertEquals("local", reopened.xdrive.service)
            assertEquals("/data/local/tmp/xdrive-fixture", reopened.xdrive.remoteFolder)
            // The same record supports switching back to an ordinary VLESS transport.
            val ordinary = reopened.copy(network = "raw", address = "127.0.0.1", port = "443")
            assertTrue(runBlocking { editor.saveForm(node.id, ordinary) })
            val changed = runBlocking { repository.loadLinksById(node.id).first() }!!
            assertEquals(node.id, changed.id)
            assertFalse(changed.isJsonConfig)
            assertNull(changed.jsonData)
        } finally {
            runBlocking { repository.allNodes.first().filter { it.remark == remark }.forEach { repository.deleteLink(it) } }
        }
    }

    @Test fun ordinaryShareCompletesBeforeDialogDisposal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName == "com.android.xrayfa.xdrivetest")
        val koin = KoinPlatform.getKoin()
        val repository = koin.get<NodeRepository>()
        val remark = "sharing-regression-${System.nanoTime()}"
        val url = "vless://00000000-0000-0000-0000-000000000001@127.0.0.1:443?security=tls"
        val node = runBlocking {
            repository.addNode(Node(protocolPrefix = "vless", address = "127.0.0.1", port = 443,
                subscriptionId = -1, remark = remark, url = url))
            repository.allNodes.first().single { it.remark == remark }
        }
        var visible by mutableStateOf(true)
        try {
            lateinit var vm: XrayViewmodel
            compose.runOnUiThread {
                vm = ViewModelProvider(compose.activity, koin.get<XrayViewmodelFactory>())[XrayViewmodel::class.java]
            }
            compose.setContent {
                val settings = ViewModelProvider(compose.activity, koin.get<SettingsViewmodelFactory>())[SettingsViewmodel::class.java]
                val apps = ViewModelProvider(compose.activity, koin.get<AppsViewmodelFactory>())[AppsViewmodel::class.java]
                MaterialTheme {
                    if (visible) AndroidPlatformRootHooks(settings, apps, vm).ShareNode(node) { visible = false }
                }
            }
            compose.waitUntil(10_000) { vm.qrBitmap.value != null }
            compose.onNodeWithText(context.getString(R.string.clipboard_export)).performClick()
            compose.waitUntil(10_000) { !visible }
            compose.runOnIdle {
                val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                assertEquals(url, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                assertNull(vm.qrBitmap.value)
            }
        } finally {
            runBlocking { repository.deleteLink(node) }
        }
    }
}
