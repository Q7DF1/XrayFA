package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.launch
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIVisualEffectView
import kotlin.math.roundToInt

private const val GLASS_MIN_IOS_MAJOR = 26

private val BarPadding = 4.dp

/**
 * iOS 26+ gets the system Liquid Glass ([UIGlassEffect]); earlier versions get a system-material
 * [UIBlurEffect]. There is deliberately no blur fallback on iOS 26.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun glassOrBlurView(): UIVisualEffectView {
    val major = UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull() ?: 0
    val effect =
        if (major >= GLASS_MIN_IOS_MAJOR) {
            UIGlassEffect()
        } else {
            UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial)
        }
    return UIVisualEffectView(effect).also { it.userInteractionEnabled = false }
}

/**
 * A native visual-effect view clipped to a capsule (corner radius = height / 2).
 * For a square size this is a circle. Sits behind Compose content in the same [Box].
 */
@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    tint: Boolean = false,
) {
    val density = LocalDensity.current
    var cornerRadiusPoints by remember { mutableFloatStateOf(0f) }
    UIKitView(
        factory = {
            glassOrBlurView().also { view ->
                if (tint) {
                    // Separates the slider lens from the bar surface it sits on.
                    view.contentView.backgroundColor = UIColor.labelColor.colorWithAlphaComponent(0.08)
                }
            }
        },
        modifier =
            modifier.onSizeChanged { size ->
                // Compose px -> UIKit points.
                cornerRadiusPoints = size.height / density.density / 2f
            },
        update = { view ->
            view.layer.cornerRadius = cornerRadiusPoints.toDouble()
            view.clipsToBounds = true
        },
    )
}

@Composable
actual fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val direction = if (isLtr) 1f else -1f
    val scope = rememberCoroutineScope()
    val currentOnIndexSettled by rememberUpdatedState(onIndexSettled)

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val paddingPx = with(density) { BarPadding.toPx() }
    val tabWidthPx = if (tabsCount > 0) (barWidthPx - 2f * paddingPx) / tabsCount else 0f

    // Slider position in tab units: 0f is the first tab, (tabsCount - 1) the last.
    val position = remember { Animatable(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, tabsCount) {
        // Follow selection changes that did not come from a drag (tab taps, restore).
        if (!isDragging) {
            position.animateTo(selectedIndex.toFloat(), spring())
        }
    }

    fun settle() {
        val target = snapTabIndex(position.value, tabsCount)
        isDragging = false
        scope.launch { position.animateTo(target.toFloat(), spring()) }
        currentOnIndexSettled(target)
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(tabsCount, tabWidthPx, direction) {
                    if (tabWidthPx <= 0f || tabsCount < 2) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next =
                                (position.value + dragAmount / tabWidthPx * direction)
                                    .coerceIn(0f, (tabsCount - 1).toFloat())
                            scope.launch { position.snapTo(next) }
                        },
                    )
                },
    ) {
        GlassSurface(Modifier.matchParentSize())
        if (tabWidthPx > 0f && tabsCount > 0) {
            GlassSurface(
                modifier =
                    Modifier
                        .offset { IntOffset((paddingPx + position.value * tabWidthPx).roundToInt(), 0) }
                        .width(with(density) { tabWidthPx.toDp() })
                        .fillMaxHeight()
                        .padding(vertical = BarPadding),
                tint = true,
            )
        }
        content()
    }
}

@Composable
actual fun ProvideFloatingNavBackdrop(content: @Composable () -> Unit) {
    content()
}

@Composable
actual fun FloatingNavBackdropSource(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) { content() }
}

@Composable
actual fun FloatingNavSearchChrome(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.matchParentSize())
        content()
    }
}
