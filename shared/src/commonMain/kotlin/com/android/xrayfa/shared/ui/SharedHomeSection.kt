package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.home.HomeBrandHeroLayout
import com.android.xrayfa.shared.ui.home.HomeLayoutCallbacks
import com.android.xrayfa.shared.ui.home.HomeMapIssue
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.stringResource

@Composable
fun SharedHomeSection(
    component: HomeComponent,
    modifier: Modifier = Modifier,
    showNodeCard: Boolean = true,
    onConnectToggle: (() -> Unit)? = null,
    labels: HomeUiLabels = HomeUiLabels(),
    mapLocations: HomeMapLocations = HomeMapLocations(),
    scrollEnabled: Boolean = true,
    largeStatusLabel: Boolean = false,
    bottomContentPadding: Dp = 0.dp,
) {
    val state by component.state.subscribeAsState()
    val locations = if (state.connectMapEnabled) {
        mapLocations.copy(user = state.connectMapOrigin?.let {
            HomeMapLocation("", null, null, null, it.latitude, it.longitude)
        })
    } else HomeMapLocations()
    val snackbar = remember { SnackbarHostState() }
    val issue = locations.issue ?: HomeMapIssue.ORIGIN_MISSING.takeIf {
        state.connectMapEnabled && state.connectMapOrigin == null
    }
    val issueMessage = issue?.let {
        stringResource(when (it) {
            HomeMapIssue.DATABASE_MISSING -> Res.string.map_database_missing
            HomeMapIssue.DATABASE_INVALID -> Res.string.map_database_invalid
            HomeMapIssue.ORIGIN_MISSING -> Res.string.map_origin_missing
            HomeMapIssue.SERVER_UNAVAILABLE -> Res.string.map_server_unavailable
            HomeMapIssue.EXIT_UNAVAILABLE -> Res.string.map_exit_unavailable
            HomeMapIssue.EXIT_TIMEOUT -> Res.string.map_exit_timeout
            HomeMapIssue.EXIT_SERVICE_ERROR -> Res.string.map_exit_service_error
            HomeMapIssue.EXIT_RATE_LIMITED -> Res.string.map_exit_rate_limited
        })
    }
    LaunchedEffect(issueMessage) {
        if (issueMessage != null) snackbar.showSnackbar(issueMessage)
    }
    Box(modifier = modifier) {
        HomeBrandHeroLayout(
            state = state,
            labels = labels,
            mapLocations = locations,
            showNodeCard = showNodeCard,
            callbacks =
                HomeLayoutCallbacks(
                    onConnectToggle = onConnectToggle ?: component::onConnectToggle,
                    onTestDelay = component::onTestDelay,
                    onRestoreGeodata = component::onRestoreGeodata,
                    onRetryStartup = if (state.isConnected) component::onRetryStartup else onConnectToggle ?: component::onRetryStartup,
                ),
            scrollEnabled = scrollEnabled,
            largeStatusLabel = largeStatusLabel,
            bottomContentPadding = bottomContentPadding,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
