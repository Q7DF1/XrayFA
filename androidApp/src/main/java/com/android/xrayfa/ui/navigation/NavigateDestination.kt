package com.android.xrayfa.ui.navigation

import androidx.navigation3.runtime.NavKey


interface NavigateDestination: NavKey {
    val route: String
    val title:  Int
}

val list_navigation: List<NavigateDestination> = listOf(Config,Home)









