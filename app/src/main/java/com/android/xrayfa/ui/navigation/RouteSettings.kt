package com.android.xrayfa.ui.navigation

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
object RouteSettings: NavigateDestination {
    override val route: String
        get() = "routeSettings"
    override val title: Int
        get() = R.string.route_settings_title
}