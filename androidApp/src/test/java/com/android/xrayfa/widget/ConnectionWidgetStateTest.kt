package com.android.xrayfa.widget

import com.android.xrayfa.common.core.StartupFailureKind
import com.android.xrayfa.common.core.StartupStage
import com.android.xrayfa.common.core.VpnStartupFailure
import com.android.xrayfa.common.core.VpnStartupState
import com.android.xrayfa.core.VpnSessionTraffic
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionWidgetStateTest {
    private val failure = VpnStartupFailure(StartupFailureKind.CORE_INITIALIZATION, StartupStage.CORE, "test")

    @Test fun retryShowsConnectingWhilePreviousFailureIsStillStored() {
        assertEquals(ConnectionWidgetState.CONNECTING,
            connectionWidgetState(true, false, VpnStartupState(2, pending = true, failure = failure)))
    }

    @Test fun restartingConnectionShowsProgressUntilAttemptCompletes() {
        assertEquals(ConnectionWidgetState.CONNECTING,
            connectionWidgetState(true, true, VpnStartupState(2, pending = true)))
    }

    @Test fun failedRestartDoesNotHideStillRunningConnection() {
        assertEquals(ConnectionWidgetState.CONNECTED,
            connectionWidgetState(true, true, VpnStartupState(2, failure = failure)))
    }

    @Test fun missingNodeOffersSetupEvenAfterAStartFailure() {
        assertEquals(ConnectionWidgetState.EMPTY,
            connectionWidgetState(false, false, VpnStartupState(1, failure = failure)))
    }

    @Test fun failedAttemptOffersRetryAndCleanStopOffersConnect() {
        assertEquals(ConnectionWidgetState.FAILED,
            connectionWidgetState(true, false, VpnStartupState(1, failure = failure)))
        assertEquals(ConnectionWidgetState.DISCONNECTED,
            connectionWidgetState(true, false, VpnStartupState(1)))
    }

    @Test fun readAndResetSnapshotsAccumulateOncePerPollAndNewSessionStartsEmpty() {
        val first = VpnSessionTraffic(startedAtElapsed = 100, available = true)
            .add(1024, 2048).add(512, 4096).add(-1, -3)
        assertEquals(1536L, first.uploadedBytes)
        assertEquals(6144L, first.downloadedBytes)
        val next = VpnSessionTraffic(startedAtElapsed = 200, available = true)
        assertEquals(0L, next.uploadedBytes)
        assertEquals(0L, next.downloadedBytes)
    }
}
