package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable


@Serializable
data object Settings: NavigateDestination {
    override val route: String
        get() = "settings"
    override val title: Int
        get() = R.string.settings_title
}