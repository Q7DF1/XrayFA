package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.shared.ui.settings.SharedRouteSettingsScreen
import com.android.xrayfa.shared.ui.rememberRouteSettingsUiLabels
import com.android.xrayfa.ui.navigation.RouteSettings
import com.android.xrayfa.ui.settings.rememberAndroidSettingsComponent

@Composable
fun RouteSettingsScreen(
    sharedTransitionScope: SharedTransitionScope,
) {
    val sharedSettingsComponent = rememberAndroidSettingsComponent()
    val labels = rememberRouteSettingsUiLabels()

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
