package com.android.v2rayForAndroidUI.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.v2rayForAndroidUI.ui.navigation.NavigateDestination
import kotlin.collections.forEach


@Composable
fun XrayBottomNav(
    items: List<NavigateDestination>,
    selectedRoute: String,
    onItemSelected: (NavigateDestination) -> Unit,
    labelProvider: (NavigateDestination) -> String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
) {
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        color = backgroundColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val iconScale by animateFloatAsState(if (selected) 1.14f else 1f)
                val labelPadding by animateDpAsState(if (selected) 8.dp else 0.dp)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) selectedColor.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onItemSelected(item) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.route,
                        tint = if (selected) selectedColor else unselectedColor,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(iconScale)
                    )
                    Spacer(Modifier.width(labelPadding))
                    Text(
                        text = labelProvider(item),
                        color = if (selected) selectedColor else unselectedColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}