package com.android.xrayfa.shared.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
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
    UIKitViewController(
        factory = { FloatingTabController { callback(it) } },
        update = { it.update(items, selectedIndex) },
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

    fun update(items: List<FloatingNavItem>, selected: Int) {
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
            CGRectMake(24.0, 24.0, size.width - 48.0, size.height - 48.0)
        }
        bar.setFrame(convertRect(rect, toView = owner))
        if (bar.superview !== owner) owner.addSubview(bar)
    }
}
