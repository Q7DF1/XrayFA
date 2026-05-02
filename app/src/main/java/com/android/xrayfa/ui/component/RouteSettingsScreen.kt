package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.ui.navigation.RouteSettings


@Composable
fun RouteSettingsScreen(
    sharedTransitionScope: SharedTransitionScope,
) {
    //TODO SettingsScreen
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier.fillMaxSize()
                .sharedElement(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState(key = RouteSettings.route),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current
                )
        ) {
            Text(
                text = "TODO",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}