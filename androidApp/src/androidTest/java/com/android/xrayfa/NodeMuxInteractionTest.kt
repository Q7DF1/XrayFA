package com.android.xrayfa

import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.core.toCoreStartOptions
import com.android.xrayfa.config.NodeMuxConfig
import com.android.xrayfa.database.XrayFADatabase
import com.android.xrayfa.database.migration.MIGRATION_5_6
import com.android.xrayfa.model.MuxObject
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform

@RunWith(AndroidJUnit4::class)
class NodeMuxInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = NodeMuxConfig.encode(MuxObject(concurrency = 4, xudpConcurrency = 16))

    @Before fun requireIsolatedPackage() {
        assumeTrue(context.packageName == "com.android.xrayfa.muxtest")
    }

    @Test fun androidServiceOptionsRetainMuxForEachHop() {
        val options = com.android.xrayfa.core.StartOptions("vless://main", preUrl = "socks://pre", nextUrl = "http://next",
            muxSettings = settings, preMuxSettings = "pre-mux", nextMuxSettings = "next-mux")
        val parcel = android.os.Parcel.obtain()
        try {
            options.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = com.android.xrayfa.core.StartOptions.CREATOR.createFromParcel(parcel)
            assertEquals(options, restored)
            val core = restored.toCoreStartOptions()
            assertEquals(settings, core.muxSettings)
            assertEquals("pre-mux", core.preMuxSettings)
            assertEquals("next-mux", core.nextMuxSettings)
        } finally {
            parcel.recycle()
        }
    }

    @Test fun switchingFromVisionToXdriveUpdatesVisibleMuxRequirements() {
        val editor = KoinPlatform.getKoin().get<NodeFormEditor>()
        val mux = NodeMuxConfig.encode(MuxObject(concurrency = -1, xudpConcurrency = -1, xudpProxyUDP443 = "skip"))
        compose.setContent {
            MaterialTheme {
                SharedEditScreen(0, "vless", "vless://00000000-0000-0000-0000-000000000001@example.com:443?security=tls&flow=xtls-rprx-vision",
                    "", editor, onBack = {}, onSave = {}, initialMuxSettings = mux)
            }
        }
        compose.onNodeWithContentDescription("Network: raw").performScrollTo().performClick()
        compose.onNodeWithText("xdrive", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Flow: none").performScrollTo().assertExists()
        compose.onNodeWithText("Show advanced settings").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("TCP concurrency (−1 to 128)"))
            .performScrollTo().assertTextContains("8")
        compose.onNode(hasSetTextAction() and hasText("XUDP concurrency (−1 to 1024)"))
            .performScrollTo().assertTextContains("8")
        compose.onNodeWithContentDescription("UDP/443 policy: Reject").performScrollTo().performClick()
        compose.onNodeWithText("Bypass Mux", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Reject", useUnmergedTree = true).performClick()
    }

    @Test fun editSaveReopenAndRemoveMuxInRoom() {
        val editor = KoinPlatform.getKoin().get<NodeFormEditor>()
        val repository = KoinPlatform.getKoin().get<NodeRepository>()
        val title = "mux-ui-${System.nanoTime()}"
        val link = "vless://00000000-0000-0000-0000-000000000001@example.com:443?security=tls"
        var submitted: NodeEditForm? = null
        val savedNode = mutableStateOf<com.android.xrayfa.model.Node?>(null)
        compose.setContent {
            MaterialTheme {
                val saved = savedNode.value
                SharedEditScreen(saved?.id ?: 0, "vless", saved?.url ?: link, title, editor,
                    onBack = {}, onSave = { submitted = it }, initialMuxSettings = saved?.muxSettings)
            }
        }
        compose.onNodeWithText("Show advanced settings").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Mux multiplexing: Not configured").performScrollTo().performClick()
        compose.onNodeWithText("Enabled", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction() and hasText("TCP concurrency (−1 to 128)"))
            .performScrollTo().performTextReplacement("4")
        compose.onNodeWithContentDescription("UDP/443 policy: Reject").performScrollTo().assertExists()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.cacheDir, "mux-editor.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle { assertNotNull(submitted) }
        val node = runBlocking {
            assertTrue(editor.saveForm(0, requireNotNull(submitted)))
            repository.allNodes.first().single { it.remark == title }
        }
        try {
            assertEquals(4, NodeMuxConfig.decode(requireNotNull(node.muxSettings)).concurrency)
            compose.runOnIdle { savedNode.value = node; submitted = null }
            compose.onNodeWithText("Show advanced settings").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("TCP concurrency (−1 to 128)"))
                .performScrollTo().assertTextContains("4")
            compose.onNodeWithContentDescription("Mux multiplexing: Enabled").performScrollTo().performClick()
            compose.onNodeWithText("Not configured", useUnmergedTree = true).performClick()
            compose.onNodeWithContentDescription("Save").performClick()
            compose.runOnIdle { assertNotNull(submitted) }
            runBlocking {
                assertTrue(editor.saveForm(node.id, requireNotNull(submitted)))
                assertNull(repository.loadLinksById(node.id).first()!!.muxSettings)
            }
        } finally {
            runBlocking { repository.deleteLinkById(node.id) }
        }
    }

    @Test fun migrateV5PreservesNodesAndSubscriptionRefreshKeepsMux() = runBlocking {
        val name = "mux-migration-${System.nanoTime()}"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE Subscription (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mark TEXT NOT NULL, url TEXT NOT NULL, preNodeId INTEGER NOT NULL, nextNodeId INTEGER NOT NULL, isAutoUpdate INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE Node (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, protocolPrefix TEXT NOT NULL, address TEXT NOT NULL, port INTEGER NOT NULL, selected INTEGER NOT NULL, isPreNode INTEGER NOT NULL, isNextNode INTEGER NOT NULL, remark TEXT, subscriptionId INTEGER NOT NULL, favorite INTEGER NOT NULL, jsonData TEXT, url TEXT NOT NULL, countryISO TEXT NOT NULL)")
            for (column in listOf("subscriptionId", "favorite", "selected")) old.execSQL("CREATE INDEX index_Node_$column ON Node($column)")
            old.execSQL("INSERT INTO Node VALUES (1, 'vless', 'example.com', 443, 1, 0, 0, 'original', 7, 1, NULL, 'original-link', '')")
            old.version = 5
        }
        val database = Room.databaseBuilder(context, XrayFADatabase::class.java, name).addMigrations(MIGRATION_5_6).build()
        try {
            val dao = database.NodeDao()
            val migrated = dao.loadNodeById(1).first()!!
            assertNull(migrated.muxSettings)
            assertTrue(migrated.selected && migrated.favorite)
            dao.updateNodeWithOptions(1, migrated.protocolPrefix, migrated.url, null, migrated.address, migrated.port,
                migrated.remark, migrated.countryISO, settings, null)
            dao.replaceSubscriptionNodes(7, listOf(migrated.copy(id = 0)))
            assertEquals(settings, dao.queryNodeBySubscriptionId(7).single().muxSettings)
            // Subscription credential rotation retains local settings via an unambiguous endpoint/name match.
            dao.replaceSubscriptionNodes(7, listOf(migrated.copy(id = 0, url = "rotated-link")))
            assertEquals(settings, dao.queryNodeBySubscriptionId(7).single().muxSettings)
            dao.replaceSubscriptionNodes(7, listOf(migrated.copy(id = 0, url = "renamed-link", remark = "renamed")))
            assertEquals(settings, dao.queryNodeBySubscriptionId(7).single().muxSettings)
            // Different nodes and subscriptions must not inherit this option.
            dao.replaceSubscriptionNodes(7, listOf(migrated.copy(id = 0, address = "other.example", url = "new-link")))
            assertNull(dao.queryNodeBySubscriptionId(7).single().muxSettings)
            dao.replaceSubscriptionNodes(8, listOf(migrated.copy(id = 0, subscriptionId = 8)))
            assertNull(dao.queryNodeBySubscriptionId(8).single().muxSettings)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
