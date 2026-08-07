package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.value.Value

interface HomeComponent {
    val state: Value<HomeState>

    fun onConnectToggle()
}

/** Typealias for E.6e naming; prefer [HomeComponent] in new code. */
typealias HomeTabComponent = HomeComponent
