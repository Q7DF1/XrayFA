package com.android.xrayfa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.xrayfa.R


data object Logcat : NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Person
    override val route: String
        get() = "logcat"
    override val containerColor: Color
        get() = Color.White
    override val title: Int
        get() = R.string.logcat
}