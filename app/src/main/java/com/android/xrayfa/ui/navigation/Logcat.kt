package com.android.xrayfa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.android.xrayfa.R
import kotlinx.serialization.Serializable

@Serializable
data object Logcat : NavigateDestination {
    override val route: String
        get() = "logcat"
    override val title: Int
        get() = R.string.logcat
}