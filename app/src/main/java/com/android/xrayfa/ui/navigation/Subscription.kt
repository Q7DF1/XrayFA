package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data object Subscription: NavigateDestination {
    override val route: String
        get() = "subscription"
    override val title: Int
        get() = R.string.subscription_title
}