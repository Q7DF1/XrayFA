package com.android.xrayfa.shared.navigation

import kotlinx.serialization.Serializable

/** Top-level pager tabs. Order matches Android bottom nav: Config, Home. Settings is an overlay. */
@Serializable
enum class RootTab {
    Config,
    Home,
}
