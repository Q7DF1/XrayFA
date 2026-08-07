package com.android.xrayfa.ui.navigation


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey

import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data object Home: NavigateDestination{
    override val route: String
        get() = "home"
    override val title: Int
        get() = R.string.home
}




