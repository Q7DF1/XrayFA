package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext

interface PlaceholderTabComponent {
    val title: String
    val message: String
}

class DefaultPlaceholderTabComponent(
    componentContext: ComponentContext,
    override val title: String,
    override val message: String,
) : PlaceholderTabComponent,
    ComponentContext by componentContext
