package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.value.Value

interface RootComponent {
    val pages: Value<ChildPages<RootTab, Child>>

    fun selectTab(index: Int)

    sealed class Child {
        class Config(val component: PlaceholderTabComponent) : Child()

        class Home(val component: HomeTabComponent) : Child()

        class Settings(val component: PlaceholderTabComponent) : Child()
    }
}
