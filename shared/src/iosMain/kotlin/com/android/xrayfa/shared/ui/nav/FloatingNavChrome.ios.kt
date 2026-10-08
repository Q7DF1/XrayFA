package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositionLocalContext
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
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import kotlin.math.roundToInt
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIVisualEffectView
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

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
            UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular)
        } else {
            UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial)
        }
    return UIVisualEffectView(effect)
}

/**
 * Keeps the material and its foreground in one native hierarchy. A separate overlay would cover
 * Compose icons; an underlay would expose a rectangular hole in the canvas.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class GlassContentController(
    private val foreground: UIViewController,
) : UIViewController(nibName = null, bundle = null) {
    private val glass = glassOrBlurView()

    init {
        glass.userInteractionEnabled = true
        view = glass
        addChildViewController(foreground)
        foreground.view.backgroundColor = UIColor.clearColor
        glass.contentView.addSubview(foreground.view)
        foreground.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        foreground.view.setFrame(glass.bounds)
        glass.layer.cornerRadius = glass.bounds.useContents { size.height / 2.0 }
        glass.clipsToBounds = true
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun GlassContainer(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val locals = currentCompositionLocalContext
    val latestContent by rememberUpdatedState(content)
    UIKitViewController(
        factory = {
            GlassContentController(
                ComposeUIViewController(configure = { opaque = false }) {
                    CompositionLocalProvider(locals) { latestContent() }
                },
            )
        },
        modifier = modifier,
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true),
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

    GlassContainer(modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
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
            if (tabWidthPx > 0f && tabsCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset((paddingPx + position.value * tabWidthPx).roundToInt(), 0) }
                            .width(with(density) { tabWidthPx.toDp() })
                            .fillMaxHeight()
                            .padding(vertical = BarPadding)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
                )
            }
            content()
        }
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
    GlassContainer(modifier) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
