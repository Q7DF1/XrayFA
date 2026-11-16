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
import com.android.xrayfa.database.XrayFADatabase
import com.android.xrayfa.database.migration.MIGRATION_5_6
import com.android.xrayfa.model.Node
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import com.android.xrayfa.shared.vpn.VpnStartOptionsResolver
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
class NodePreProxyInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun requireIsolatedPackage() {
        assumeTrue(context.packageName in setOf("com.android.xrayfa.pretest", "com.android.xrayfa.pretestv6"))
    }

    @Test fun selectSaveReopenAndResolveIndividualPreNode() = runBlocking {
        val koin = KoinPlatform.getKoin()
        val repo = koin.get<NodeRepository>()
        val editor = koin.get<NodeFormEditor>()
        val suffix = System.nanoTime()
        val hopName = "fixture-pre-$suffix"
        val hopUrl = "socks://pre-$suffix.example:1080"
        val mainUrl = "socks://main-$suffix.example:1080"
        repo.addNode(Node(protocolPrefix = "socks", address = "pre.example", port = 1080,
            subscriptionId = -1, remark = hopName, url = hopUrl),
            Node(protocolPrefix = "socks", address = "main.example", port = 1080,
                subscriptionId = -1, remark = "fixture-main", url = mainUrl))
        val created = repo.allNodes.first()
        val hop = created.single { it.url == hopUrl }
        val main = created.single { it.url == mainUrl }
        val current = mutableStateOf(main)
        var submitted: NodeEditForm? = null
        try {
            compose.setContent {
                MaterialTheme {
                    val node = current.value
                    SharedEditScreen(node.id, node.protocolPrefix, node.url, node.remark, editor,
                        onBack = {}, onSave = { submitted = it }, initialPreNodeId = node.preNodeId)
                }
            }
            compose.onNodeWithTag("node-pre-picker").performClick()
            compose.onNodeWithText(hopName).performScrollTo().performClick()
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.onNode(hasText(hopName) and hasAnyAncestor(hasTestTag("node-pre-picker"))).assertExists()
            compose.onNodeWithContentDescription("Save").performClick()
            compose.runOnIdle {
                assertNotNull("Save must submit a valid form", submitted)
                assertEquals(hop.id, submitted?.preNodeId)
            }
            assertTrue(editor.saveForm(main.id, requireNotNull(submitted)))
            val saved = repo.loadLinksById(main.id).first()!!
            assertEquals(hop.id, saved.preNodeId)
            compose.runOnIdle { current.value = saved }
            compose.onNodeWithText(hopName).assertExists()
            assertEquals(hopUrl, koin.get<VpnStartOptionsResolver>().resolveNode(saved).preUrl)
            repo.clearSelection()
            repo.updateSelectById(saved.id, true)
            val androidOptions = koin.get<com.android.xrayfa.core.XrayBaseServiceManager>().getConfigInformation()
            assertEquals(hopUrl, androidOptions?.preUrl)
            repo.deleteLinkById(hop.id)
            try {
                koin.get<VpnStartOptionsResolver>().resolveNode(saved)
                fail("Removed pre-node must not silently become a direct connection")
            } catch (_: IllegalArgumentException) { }
        } finally {
            withContext(NonCancellable) {
                repo.deleteLinkById(main.id)
                repo.deleteLinkById(hop.id)
            }
        }
    }

    @Test fun migrationAndSubscriptionRefreshRetainPreNodeReferences() = runBlocking {
        val name = "pre-migration-${System.nanoTime()}"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE Subscription (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, mark TEXT NOT NULL, url TEXT NOT NULL, preNodeId INTEGER NOT NULL, nextNodeId INTEGER NOT NULL, isAutoUpdate INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE Node (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, protocolPrefix TEXT NOT NULL, address TEXT NOT NULL, port INTEGER NOT NULL, selected INTEGER NOT NULL, isPreNode INTEGER NOT NULL, isNextNode INTEGER NOT NULL, remark TEXT, subscriptionId INTEGER NOT NULL, favorite INTEGER NOT NULL, jsonData TEXT, url TEXT NOT NULL, countryISO TEXT NOT NULL)")
            for (column in listOf("subscriptionId", "favorite", "selected")) old.execSQL("CREATE INDEX index_Node_$column ON Node($column)")
            old.execSQL("INSERT INTO Node VALUES (1, 'socks', 'main.example', 1080, 1, 0, 0, 'main', 7, 1, NULL, 'socks://main.example:1080', '')")
            old.execSQL("INSERT INTO Node VALUES (2, 'socks', 'pre.example', 1080, 0, 0, 0, 'pre', 8, 0, NULL, 'socks://pre.example:1080', '')")
            old.version = 5
        }
        val database = Room.databaseBuilder(context, XrayFADatabase::class.java, name)
            .addMigrations(MIGRATION_5_6).build()
        try {
            val dao = database.NodeDao()
            val main = dao.loadNodeById(1).first()!!
            val hop = dao.loadNodeById(2).first()!!
            assertNull(main.preNodeId)
            assertNull(main.muxSettings)
            assertTrue(main.selected && main.favorite)
            dao.updateNodeWithOptions(main.id, main.protocolPrefix, main.url, main.jsonData, main.address,
                main.port, main.remark, main.countryISO, main.muxSettings, 2)
            dao.replaceSubscriptionNodes(7, listOf(main.copy(id = 0, url = "socks://user:changed@main.example:1080")))
            val refreshed = dao.loadNodeById(1).first()!!
            assertEquals(2, refreshed.preNodeId)
            assertTrue(refreshed.selected && refreshed.favorite)
            dao.replaceSubscriptionNodes(8, listOf(hop.copy(id = 0, url = "socks://user:changed@pre.example:1080")))
            assertNotNull(dao.loadNodeById(refreshed.preNodeId!!).first())
            // Two new credentials matching one old node must not arbitrarily inherit its ID.
            dao.replaceSubscriptionNodes(8, listOf(
                hop.copy(id = 0, url = "socks://one:changed@pre.example:1080"),
                hop.copy(id = 0, url = "socks://two:changed@pre.example:1080")))
            assertNull(dao.loadNodeById(refreshed.preNodeId!!).first())
            assertEquals(2, dao.loadNodeById(1).first()!!.preNodeId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
