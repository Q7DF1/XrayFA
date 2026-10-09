package com.android.xrayfa.shared.ui.nav

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import com.android.xrayfa.shared.ui.transitions.LocalStackAnimationScope
import com.android.xrayfa.shared.ui.transitions.XrayMotion
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.*
import platform.darwin.NSObject

/** UIKit owns hit testing, dragging, selection and the Liquid Glass lens. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
internal fun NativeFloatingTabBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    val callback by rememberUpdatedState(onSelected)
    val visibility = LocalStackAnimationScope.current
    val alpha = if (visibility != null) {
        val value by visibility.transition.animateFloat(
            transitionSpec = { XrayMotion.EffectsFloat },
            label = "Native floating navigation opacity",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        value
    } else 1f
    val interactive = visibility == null || visibility.transition.targetState == EnterExitState.Visible
    UIKitViewController(
        factory = { FloatingTabController { callback(it) } },
        update = { it.update(items, selectedIndex, alpha, interactive) },
        modifier = modifier.layout { measurable, constraints ->
            val outset = 24.dp.roundToPx()
            val child = measurable.measure(constraints.offset(horizontal = outset * 2, vertical = outset * 2))
            layout(child.width - outset * 2, child.height - outset * 2) { child.place(-outset, -outset) }
        },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true),
    )
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class FloatingTabController(onSelected: (Int) -> Unit) : UIViewController(nibName = null, bundle = null) {
    private val bar = UITabBar(frame = CGRectMake(0.0, 0.0, 280.0, 70.0))
    private var itemKeys: List<String> = emptyList()
    private val selectionDelegate = object : NSObject(), UITabBarDelegateProtocol {
        override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
            onSelected(didSelectItem.tag.toInt())
        }
    }

    init {
        bar.delegate = selectionDelegate
        view = FloatingTabAnchor(bar)
    }

    fun update(items: List<FloatingNavItem>, selected: Int, alpha: Float, interactive: Boolean) {
        // This bar is a window sibling, so it does not inherit the Compose
        // anchor's opacity. Drive both from the same visibility transition.
        bar.alpha = alpha.toDouble()
        bar.userInteractionEnabled = interactive
        val keys = items.map { "${it.id}:${it.label}:${it.icon.name}" }
        if (keys != itemKeys) {
            itemKeys = keys
            bar.items = items.mapIndexed { index, item ->
                val symbol = when (item.icon.name.substringAfterLast('.')) {
                    "Tune" -> "slider.horizontal.3"
                    "Language" -> "globe"
                    "KeyboardArrowUp" -> "chevron.up"
                    "KeyboardArrowDown" -> "chevron.down"
                    else -> "circle"
                }
                UITabBarItem(title = item.label, image = UIImage.systemImageNamed(symbol), tag = index.toLong())
            }
        }
        val item = bar.items?.getOrNull(selected) as? UITabBarItem
        if (bar.selectedItem !== item) bar.selectedItem = item
    }
}

/**
 * The owning window supplies UIKit's full layout context and live page backdrop.
 * The Compose interop anchor still owns placement and lifetime. Placing a tab bar inside
 * its small interop controller causes UIKit to compress the icon/title layout.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class FloatingTabAnchor(private val bar: UITabBar) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        backgroundColor = UIColor.clearColor
        opaque = false
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        val owner = window
        if (owner == null) bar.removeFromSuperview()
        else layoutTabBar()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        layoutTabBar()
    }

    private fun layoutTabBar() {
        val owner = window ?: return
        // Attach only after Compose supplies real bounds. Letting UIKit first lay
        // out a zero-sized tab bar caches compact item metrics and hides captions.
        val rect = bounds.useContents {
            if (size.width <= 48.0 || size.height <= 48.0) return
            val width = size.width - 48.0
            // UIKit needs its window context to measure the host, whose height
            // includes native optical padding and safe-area handling.
            val hostWidth = width + 44.0
            val hostHeight = if (bar.superview === owner) {
                bar.sizeThatFits(CGSizeMake(hostWidth, 0.0)).useContents { height }
            } else {
                size.height - 40.0
            }
            CGRectMake(2.0, 24.0, hostWidth, hostHeight)
        }
        bar.setFrame(convertRect(rect, toView = owner))
        if (bar.superview !== owner) {
            owner.addSubview(bar)
            // Measure again only after attachment. Forcing the shared visible
            // height onto the native host during entry/exit changes its item
            // metrics and leaves captions overlapping icons on return.
            val nativeRect = rect.useContents {
                val height = bar.sizeThatFits(CGSizeMake(size.width, 0.0)).useContents { height }
                CGRectMake(origin.x, origin.y, size.width, height)
            }
            bar.setFrame(convertRect(nativeRect, toView = owner))
        }
    }
}
