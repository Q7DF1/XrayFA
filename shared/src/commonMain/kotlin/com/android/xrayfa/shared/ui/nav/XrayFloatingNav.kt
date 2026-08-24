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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.shared.navigation.RootTab
import kotlin.math.floor

private val BarHeight = 64.dp
private val BarCorner = 32.dp
private val IndicatorInset = 6.dp
private val IndicatorCorner = 28.dp

data class FloatingNavItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

fun RootTab.toFloatingNavItem(): FloatingNavItem =
    when (this) {
        RootTab.Config -> FloatingNavItem(id = name, icon = Icons.Default.Tune, label = "Config")
        RootTab.Home -> FloatingNavItem(id = name, icon = Icons.Default.Language, label = "Home")
        RootTab.Settings -> FloatingNavItem(id = name, icon = Icons.Default.Tune, label = "Settings")
    }

/** Floating pill bottom nav shared by Android and iOS. */
@Composable
fun XrayFloatingNav(
    items: List<FloatingNavItem>,
    selectedId: String,
    onItemSelected: (FloatingNavItem) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
) {
    val density = LocalDensity.current
    val itemCount = items.size.coerceAtLeast(1)
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val animOffsetX = remember { Animatable(0f) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp, start = 8.dp, end = 8.dp),
    ) {
        val barWidth =
            if (constraints.maxWidth <= 0 ||
                constraints.maxWidth == androidx.compose.ui.unit.Constraints.Infinity
            ) {
                280.dp
            } else {
                (maxWidth * 0.75f).coerceAtMost(320.dp)
            }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .width(barWidth)
                        .height(BarHeight)
                        .clip(RoundedCornerShape(BarCorner))
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(BarCorner),
                        ),
                shape = RoundedCornerShape(BarCorner),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 2.dp,
                tonalElevation = 4.dp,
            ) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(BarCorner)),
                ) {
                    val maxWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val slotWidthPx = maxWidthPx / itemCount
                    val slotStartPx = slotWidthPx * selectedIndex
                    val maxOffsetPx = (maxWidthPx - slotWidthPx).coerceAtLeast(0f)
                    val slotWidthDp = with(density) { floor(slotWidthPx.toDouble()).toFloat().toDp() }

                    LaunchedEffect(selectedIndex, slotWidthPx, maxWidthPx) {
                        val target = slotStartPx.coerceIn(0f, maxOffsetPx)
                        animOffsetX.snapTo(animOffsetX.value.coerceIn(0f, maxOffsetPx))
                        animOffsetX.animateTo(
                            targetValue = target,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        )
                    }

                    // Sliding capsule inset 6dp from the outer pill (matches Android).
                    Box(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset(animOffsetX.value.coerceIn(0f, maxOffsetPx).toInt(), 0)
                                }
                                .width(slotWidthDp)
                                .fillMaxHeight()
                                .padding(IndicatorInset)
                                .clip(RoundedCornerShape(IndicatorCorner))
                                .background(selectedColor.copy(alpha = 0.12f)),
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        items.forEachIndexed { index, item ->
                            val selected = index == selectedIndex
                            val iconScale by
                                animateFloatAsState(
                                    targetValue = if (selected) 1.15f else 1f,
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
                                        ) { onItemSelected(item) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = contentColor,
                                        modifier =
                                            Modifier
                                                .size(26.dp)
                                                .scale(iconScale),
                                    )
                                    if (selected) {
                                        Text(
                                            text = item.label,
                                            color = contentColor,
                                            fontSize = 11.sp,
                                            lineHeight = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
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
