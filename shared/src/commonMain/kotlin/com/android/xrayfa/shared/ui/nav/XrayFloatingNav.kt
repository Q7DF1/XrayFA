package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.shared.navigation.RootTab

/** Match the native iOS floating capsule while exceeding the 48dp touch target. */
val FloatingNavBarHeight = 62.dp
val FloatingNavBottomMargin = 8.dp
/** Extra space so the last Config row can rest above the pill. */
val FloatingNavExtraContentPadding = 24.dp
private val BottomFadeExtra = 36.dp

@Composable
fun rememberFloatingNavClearance(extraAboveBar: Dp = FloatingNavExtraContentPadding): Dp {
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return systemBottom + FloatingNavBarHeight + FloatingNavBottomMargin + extraAboveBar
}

@Composable
fun FloatingNavBottomFade(modifier: Modifier = Modifier) {
    if (!floatingNavNeedsBottomFade()) return
    val fadeHeight = rememberFloatingNavClearance(extraAboveBar = BottomFadeExtra)
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(fadeHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to background.copy(alpha = 0.55f),
                        0.72f to background.copy(alpha = 0.88f),
                        1f to background,
                    ),
                ),
    )
}

data class FloatingNavItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
)

fun RootTab.toFloatingNavItem(): FloatingNavItem =
    when (this) {
        RootTab.Config -> FloatingNavItem(id = name, icon = Icons.Default.Tune, label = "Config")
        RootTab.Home -> FloatingNavItem(id = name, icon = Icons.Default.Language, label = "Home")
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
    trailingContent: @Composable (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingContentDescription: String = "",
    trailingNativeSystemImage: String? = null,
) {
    val itemCount = items.size.coerceAtLeast(1)
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Share UIKit's compact item geometry across platforms: two 90dp
        // items plus the capsule's 4dp padding on each side = 188dp.
        val preferredBarWidth = 90.dp * itemCount + 8.dp
        val barWidth = if (constraints.maxWidth <= 0 ||
            constraints.maxWidth == androidx.compose.ui.unit.Constraints.Infinity
        ) {
            preferredBarWidth
        } else {
            val actionWidth = if (trailingContent != null) FloatingNavBarHeight + 12.dp else 0.dp
            preferredBarWidth.coerceAtMost((maxWidth - actionWidth).coerceAtLeast(0.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingNavChrome(
                selectedIndex = selectedIndex,
                onIndexSettled = { index ->
                    items.getOrNull(index)?.let(onItemSelected)
                },
                tabsCount = itemCount,
                nativeItems = items,
                modifier = Modifier.width(barWidth).height(FloatingNavBarHeight),
            ) {
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
                                            .size(22.dp)
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
            if (trailingContent != null) {
                Box(modifier = Modifier.width(12.dp).height(FloatingNavBarHeight))
                FloatingNavSearchChrome(
                    nativeSystemImage = trailingNativeSystemImage,
                    nativeContentDescription = trailingContentDescription,
                    onClick = { onTrailingClick?.invoke() },
                    modifier = Modifier.size(FloatingNavBarHeight),
                ) {
                    trailingContent()
                }
            }
        }
    }
}
