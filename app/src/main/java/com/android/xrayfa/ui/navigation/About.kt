package com.android.xrayfa.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector


data object About : NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Person
    override val route: String
        get() = "about"
    override val containerColor: Color
        get() = Color.White
}



@Composable
fun AboutScreen() {}