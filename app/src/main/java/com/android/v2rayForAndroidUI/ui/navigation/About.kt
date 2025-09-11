package com.android.v2rayForAndroidUI.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector


data object About : NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Person
    override val route: String
        get() = "about"
}



@Composable
fun AboutScreen() {}