package com.android.v2rayForAndroidUI.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector


interface NavigateDestination {
    val icon: ImageVector
    val route: String
}


val list_navigation = listOf(Config,Home(), About)










