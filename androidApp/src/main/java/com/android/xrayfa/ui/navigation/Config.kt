package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data object Config: NavigateDestination {
    override val route: String
        get() = "config"
    override val title: Int
        get() = R.string.config
}

