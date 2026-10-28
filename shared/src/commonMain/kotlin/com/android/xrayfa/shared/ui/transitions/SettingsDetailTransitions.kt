package com.android.xrayfa.shared.ui.transitions

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Paired source/destination tokens for settings-row container transforms. */
data class SettingsDetailTransition(
    val destination: String,
    val topCorner: Dp = 0.dp,
    val bottomCorner: Dp = 0.dp,
)

object SettingsDetailTransitions {
    val ConnectMap = SettingsDetailTransition(TransitionDestinations.CONNECT_MAP)
    val Apps = SettingsDetailTransition(TransitionDestinations.APPS, topCorner = PlatformRowOuterCorner)
    val Logcat = SettingsDetailTransition(TransitionDestinations.LOGCAT)
    val Route = SettingsDetailTransition(TransitionDestinations.ROUTE, bottomCorner = PlatformRowOuterCorner)
}

@Composable
fun Modifier.settingsDetailSource(token: SettingsDetailTransition): Modifier = sharedContainer(
    destination = token.destination,
    shape = RoundedCornerShape(
        topStart = token.topCorner, topEnd = token.topCorner,
        bottomStart = token.bottomCorner, bottomEnd = token.bottomCorner,
    ),
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)

@Composable
fun Modifier.settingsDetailDestination(token: SettingsDetailTransition): Modifier = sharedContainer(
    destination = token.destination,
    shape = rememberDestinationShape(
        topStart = token.topCorner, topEnd = token.topCorner,
        bottomStart = token.bottomCorner, bottomEnd = token.bottomCorner,
    ),
    containerColor = MaterialTheme.colorScheme.background,
)
