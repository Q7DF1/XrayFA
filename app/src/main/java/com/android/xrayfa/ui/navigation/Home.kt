package com.android.xrayfa.ui.navigation


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

import com.android.xrayfa.R

data object Home: NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Home
    override val route: String
        get() = "home"
    override val containerColor: Color
        get() = Color(0xFF00BFFF)
    override val title: Int
        get() = R.string.home
}




