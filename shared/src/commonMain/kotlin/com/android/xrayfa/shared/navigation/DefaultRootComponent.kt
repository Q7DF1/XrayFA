package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.pages.ChildPages
import com.arkivanov.decompose.router.pages.Pages
import com.arkivanov.decompose.router.pages.PagesNavigation
import com.arkivanov.decompose.router.pages.childPages
import com.arkivanov.decompose.router.pages.select
import com.arkivanov.decompose.value.Value

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent,
    ComponentContext by componentContext {
    private val navigation = PagesNavigation<RootTab>()

    override val pages: Value<ChildPages<RootTab, RootComponent.Child>> =
        childPages(
            source = navigation,
            serializer = RootTab.serializer(),
            initialPages = {
                Pages(
                    items = listOf(RootTab.Config, RootTab.Home, RootTab.Settings),
                    selectedIndex = RootTab.Home.ordinal,
                )
            },
        ) { tab, childContext ->
            when (tab) {
                RootTab.Config ->
                    RootComponent.Child.Config(
                        DefaultPlaceholderTabComponent(
                            componentContext = childContext,
                            title = "Config",
                            message = "Node list and subscriptions will migrate here.",
                        ),
                    )
                RootTab.Home ->
                    RootComponent.Child.Home(
                        DefaultHomeTabComponent(componentContext = childContext),
                    )
                RootTab.Settings ->
                    RootComponent.Child.Settings(
                        DefaultPlaceholderTabComponent(
                            componentContext = childContext,
                            title = "Settings",
                            message = "Settings screens will migrate here.",
                        ),
                    )
            }
        }

    override fun selectTab(index: Int) {
        navigation.select(index = index)
    }
}
