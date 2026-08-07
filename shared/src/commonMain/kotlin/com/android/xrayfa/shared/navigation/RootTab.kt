package com.android.xrayfa.shared.navigation

import kotlinx.serialization.Serializable

/** Top-level tabs for shared Decompose shell (E.6e). Order matches Android bottom nav: Config, Home. */
@Serializable
enum class RootTab {
    Config,
    Home,
    Settings,
}
