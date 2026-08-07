package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data object Edit: NavigateDestination {
    override val route: String
        get() = "edit"
    override val title: Int
        get() = R.string.edit
}