package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R

data object Settings: NavigateDestination {
    override val route: String
        get() = "settings"
    override val title: Int
        get() = R.string.settings_title
}