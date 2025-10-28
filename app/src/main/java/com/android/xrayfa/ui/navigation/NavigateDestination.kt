package com.android.xrayfa.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector


interface NavigateDestination {
    val icon: ImageVector
    val route: String
    val containerColor: Color
    val title:  Int
    val actionIcon: @Composable (() -> Unit)
        get() = {}
}


val list_navigation = listOf(Config,Home, Logcat)










