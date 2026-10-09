package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UICornerConfiguration
import platform.UIKit.UIDevice
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIVisualEffectView
import platform.UIKit.addChildViewController
import platform.UIKit.cornerConfiguration
import platform.UIKit.didMoveToParentViewController

private const val GLASS_MIN_IOS_MAJOR = 26

private val BarPadding = 4.dp
private const val GlassOutsetPoints = 24.0
private val GlassOutset = 24.dp

/**
 * iOS 26+ gets the system Liquid Glass ([UIGlassEffect]); earlier versions get a system-material
 * [UIBlurEffect]. There is deliberately no blur fallback on iOS 26.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun glassOrBlurView(isSelection: Boolean = false): UIVisualEffectView {
    val major = UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull() ?: 0
    val effect =
        if (major >= GLASS_MIN_IOS_MAJOR) {
            UIGlassEffect.effectWithStyle(
                UIGlassEffectStyle.UIGlassEffectStyleRegular,
            ).apply { interactive = !isSelection }
        } else {
            UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial)
        }
    return UIVisualEffectView(effect).apply {
        backgroundColor = UIColor.clearColor
        contentView.backgroundColor = UIColor.clearColor
    }
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
    private val host = UIView().apply {
        backgroundColor = UIColor.clearColor
        opaque = false
    }
    private var lens: UIVisualEffectView? = null
    private var lensPosition: Float? = null
    private var lensTabsCount = 0
    private var lensIsLtr = true

    fun updateLens(position: Float?, tabsCount: Int, isLtr: Boolean) {
        lensPosition = position
        lensTabsCount = tabsCount
        lensIsLtr = isLtr
        if (position == null || tabsCount <= 0) {
            lens?.removeFromSuperview()
            lens = null
            return
        }
        if (lens == null) {
            lens = glassOrBlurView(isSelection = true).also {
                it.userInteractionEnabled = false
                glass.contentView.insertSubview(it, atIndex = 0)
            }
        }
        layoutLens()
    }

    private fun layoutLens() {
        val selection = lens ?: return
        val position = lensPosition ?: return
        glass.bounds.useContents {
            val padding = 4.0
            val width = ((size.width - padding * 2) / lensTabsCount).coerceAtLeast(0.0)
            val height = (size.height - padding * 2).coerceAtLeast(0.0)
            val index = if (lensIsLtr) position else lensTabsCount - 1 - position
            selection.setFrame(CGRectMake(padding + index * width, padding, width, height))
            val major = UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull() ?: 0
            if (major >= GLASS_MIN_IOS_MAJOR) {
                selection.cornerConfiguration = UICornerConfiguration.capsuleConfiguration()
            } else {
                selection.layer.cornerRadius = height / 2
                selection.clipsToBounds = true
            }
        }
    }

    init {
        glass.userInteractionEnabled = true
        view = host
        host.addSubview(glass)
        addChildViewController(foreground)
        foreground.view.backgroundColor = UIColor.clearColor
        glass.contentView.addSubview(foreground.view)
        foreground.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        host.bounds.useContents {
            val inset = GlassOutsetPoints
            glass.setFrame(
                CGRectMake(
                    inset,
                    inset,
                    (size.width - inset * 2).coerceAtLeast(0.0),
                    (size.height - inset * 2).coerceAtLeast(0.0),
                ),
            )
        }
        foreground.view.setFrame(glass.bounds)
        layoutLens()
        val radius = glass.bounds.useContents { size.height / 2.0 }
        foreground.view.layer.cornerRadius = radius
        foreground.view.clipsToBounds = true
        val major = UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull() ?: 0
        if (major < GLASS_MIN_IOS_MAJOR) {
            glass.layer.cornerRadius = radius
            glass.clipsToBounds = true
        } else {
            // UIGlassEffect supplies its own capsule geometry and outside optical effects.
            glass.cornerConfiguration = UICornerConfiguration.capsuleConfiguration()
            glass.clipsToBounds = false
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun GlassContainer(
    modifier: Modifier,
    lensPosition: Float? = null,
    lensTabsCount: Int = 0,
    lensIsLtr: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Each Compose controller owns its layout/graphics/transition context. Only copy styling values.
    val colors by rememberUpdatedState(MaterialTheme.colorScheme)
    val typography by rememberUpdatedState(MaterialTheme.typography)
    val shapes by rememberUpdatedState(MaterialTheme.shapes)
    val contentColor by rememberUpdatedState(LocalContentColor.current)
    val textStyle by rememberUpdatedState(LocalTextStyle.current)
    val layoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val latestContent by rememberUpdatedState(content)
    UIKitViewController(
        factory = {
            GlassContentController(
                ComposeUIViewController(configure = { opaque = false }) {
                    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
                        CompositionLocalProvider(
                            LocalContentColor provides contentColor,
                            LocalTextStyle provides textStyle,
                            LocalLayoutDirection provides layoutDirection,
                        ) {
                            latestContent()
                        }
                    }
                },
            )
        },
        // UIKit's glass draws optical edges and shadows outside its bounds. The interop
        // wrapper clips to a rectangle, so reserve transparent space around the material
        // while retaining the original Compose layout size and touch targets.
        modifier = modifier.layout { measurable, constraints ->
            val outset = GlassOutset.roundToPx()
            val child = measurable.measure(constraints.offset(horizontal = outset * 2, vertical = outset * 2))
            layout(child.width - outset * 2, child.height - outset * 2) {
                child.place(-outset, -outset)
            }
        },
        update = { controller -> controller.updateLens(lensPosition, lensTabsCount, lensIsLtr) },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true),
    )
}

@Composable
actual fun FloatingNavChrome(
    selectedIndex: Int,
    onIndexSettled: (Int) -> Unit,
    tabsCount: Int,
    nativeItems: List<FloatingNavItem>,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if ((UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull() ?: 0) >= 26 && nativeItems.isNotEmpty()) {
        NativeFloatingTabBar(nativeItems, selectedIndex, onIndexSettled, modifier)
        return
    }
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

    GlassContainer(modifier, position.value, tabsCount, isLtr) {
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
    nativeSystemImage: String?,
    nativeContentDescription: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull()?.let { it >= 26 } == true && nativeSystemImage != null) {
        com.android.xrayfa.shared.ui.widgets.NativeGlassSymbolButton(onClick, nativeSystemImage, nativeContentDescription, modifier)
        return
    }
    GlassContainer(modifier) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

internal actual fun floatingNavNeedsBottomFade(): Boolean = false
