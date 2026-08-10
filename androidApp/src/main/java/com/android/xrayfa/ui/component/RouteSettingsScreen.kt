package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.shared.ui.settings.RouteSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedRouteSettingsScreen
import com.android.xrayfa.ui.navigation.RouteSettings
import com.android.xrayfa.ui.settings.rememberAndroidSettingsComponent

@Composable
fun RouteSettingsScreen(
    sharedTransitionScope: SharedTransitionScope,
) {
    val sharedSettingsComponent = rememberAndroidSettingsComponent()
    val labels =
        RouteSettingsUiLabels(
            title = stringResource(R.string.route_settings_title),
            routingModeSectionTitle = stringResource(R.string.routing_mode_label),
            routingModeGlobalLabel = stringResource(R.string.routing_mode_global),
            routingModeRouteLabel = stringResource(R.string.routing_mode_route),
        )

    with(sharedTransitionScope) {
        SharedRouteSettingsScreen(
            component = sharedSettingsComponent,
            labels = labels,
            modifier =
                Modifier
                    .fillMaxSize()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = RouteSettings.route),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    ),
        )
    }
}
