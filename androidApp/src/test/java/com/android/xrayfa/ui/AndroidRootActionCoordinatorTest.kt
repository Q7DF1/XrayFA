package com.android.xrayfa.ui

import com.android.xrayfa.ui.navigation.AndroidRootAction
import com.android.xrayfa.ui.navigation.AndroidRootActionCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidRootActionCoordinatorTest {
    @Test
    fun consumingActionDoesNotCancelSuspendedHandler() = runBlocking {
        val coordinator = AndroidRootActionCoordinator()
        val preparing = CompletableDeferred<Unit>()
        val configReady = CompletableDeferred<Unit>()
        val connected = CompletableDeferred<Unit>()
        coordinator.dispatch(AndroidRootAction.OpenQrScan)

        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.collectActions { action ->
                assertEquals(AndroidRootAction.OpenQrScan, action)
                coordinator.consume()
                preparing.complete(Unit)
                configReady.await()
                connected.complete(Unit)
            }
        }
        try {
            withTimeout(1_000) { preparing.await() }
            yield()
            assertNull(coordinator.pendingAction.value)
            assertFalse(connected.isCompleted)
            configReady.complete(Unit)
            withTimeout(1_000) { connected.await() }
        } finally {
            collector.cancelAndJoin()
        }
    }
}
