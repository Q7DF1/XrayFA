package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.ComponentContext

/** Marker for the Home tab; UI renders [SharedHomeSection] until HomeComponent state migration. */
interface HomeTabComponent

class DefaultHomeTabComponent(
    componentContext: ComponentContext,
) : HomeTabComponent,
    ComponentContext by componentContext
