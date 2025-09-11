package com.android.v2rayForAndroidUI.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.v2rayForAndroidUI.model.Node

data object Config: NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Build
    override val route: String
        get() = "config"

}


@Composable
fun ConfigScreen(
    onNavigate2Home: (Node) -> Unit
) {

}
