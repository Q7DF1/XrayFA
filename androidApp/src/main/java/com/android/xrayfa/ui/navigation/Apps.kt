package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data object Apps: NavigateDestination {
    override val route: String
        get() = "apps"
    override val title: Int
        get() = R.string.all_app_settings
}