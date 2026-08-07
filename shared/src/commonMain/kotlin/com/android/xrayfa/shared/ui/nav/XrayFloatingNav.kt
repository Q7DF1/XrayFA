package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.shared.navigation.RootTab

/** Floating pill bottom nav — visual parity with Android `XrayModernFloatingNav`. */
@Composable
fun XrayFloatingNav(
    selectedTab: RootTab,
    onTabSelected: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    unselectedColor: androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
) {
    val items = listOf(RootTab.Config, RootTab.Home)
    val density = LocalDensity.current
    val itemCount = items.size
    val selectedIndex = items.indexOf(selectedTab).coerceAtLeast(0)
    val animOffsetX = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier =
            modifier
                .padding(bottom = 4.dp, start = 8.dp, end = 8.dp)
                .wrapContentWidth(),
    ) {
        val shorterSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val barWidth = (shorterSide * 0.75f).coerceAtMost(320.dp)

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .width(barWidth)
                        .height(64.dp)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(32.dp),
                        ),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 2.dp,
                tonalElevation = 4.dp,
            ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxWidthPx = constraints.maxWidth.toFloat()
                val itemWidthPx = maxWidthPx / itemCount
                val itemWidthDp = with(density) { itemWidthPx.toDp() }

                LaunchedEffect(selectedIndex) {
                    animOffsetX.animateTo(
                        targetValue = selectedIndex * itemWidthPx,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(animOffsetX.value.toInt(), 0) }
                            .width(itemWidthDp)
                            .fillMaxHeight()
                            .padding(6.dp)
                            .background(
                                color = selectedColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(28.dp),
                            ),
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val iconScale by
                            animateFloatAsState(
                                targetValue = if (selected) 1.2f else 1f,
                                animationSpec = tween(300),
                            )
                        val contentColor by
                            animateColorAsState(
                                targetValue = if (selected) selectedColor else unselectedColor,
                                animationSpec = tween(300),
                            )

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onTabSelected(tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = tab.toNavIcon(),
                                    contentDescription = tab.toNavLabel(),
                                    tint = contentColor,
                                    modifier =
                                        Modifier
                                            .size(26.dp)
                                            .scale(iconScale),
                                )
                                if (selected) {
                                    Text(
                                        text = tab.toNavLabel(),
                                        color = contentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private fun RootTab.toNavIcon(): ImageVector =
    when (this) {
        RootTab.Config -> Icons.Default.Tune
        RootTab.Home -> Icons.Default.Language
        RootTab.Settings -> Icons.Default.Tune
    }

private fun RootTab.toNavLabel(): String =
    when (this) {
        RootTab.Config -> "Config"
        RootTab.Home -> "Home"
        RootTab.Settings -> "Settings"
    }
