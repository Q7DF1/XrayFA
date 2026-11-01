package com.android.xrayfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.xrayfa.database.AndroidXrayDatabaseFactory
import com.android.xrayfa.database.entity.NodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionNodeTransactionTest {
    @Test
    fun failedReplacementPreservesPreviousNodes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".review")) { "Run with isolated APPLICATION_ID=com.android.xrayfa.review" }
        val dao = AndroidXrayDatabaseFactory.getNodeDao(context)
        val subscriptionId = -10001
        val otherId = -10002
        fun node(subId: Int) = NodeEntity(
            protocolPrefix = "vless", address = "example.invalid", port = 443,
            subscriptionId = subId, url = "vless://test-id@example.invalid:443",
        )
        dao.deleteBySubscriptionId(subscriptionId)
        dao.deleteBySubscriptionId(otherId)
        try {
            dao.addNode(node(subscriptionId), node(otherId))
            val original = dao.queryNodeBySubscriptionId(subscriptionId)
            val occupiedId = dao.queryNodeBySubscriptionId(otherId).single().id
            var failed = false
            try {
                dao.replaceSubscriptionNodes(subscriptionId, listOf(node(subscriptionId).copy(id = occupiedId)))
            } catch (_: Exception) {
                failed = true
            }
            assertTrue("The duplicate primary key must fail", failed)
            assertEquals(original, dao.queryNodeBySubscriptionId(subscriptionId))
            dao.replaceSubscriptionNodes(subscriptionId, listOf(node(subscriptionId).copy(remark = "replacement")))
            assertEquals("replacement", dao.queryNodeBySubscriptionId(subscriptionId).single().remark)
            assertEquals(1, dao.queryNodeBySubscriptionId(otherId).size)
        } finally {
            dao.deleteBySubscriptionId(subscriptionId)
            dao.deleteBySubscriptionId(otherId)
        }
    }
}
