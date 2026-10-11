package com.android.xrayfa.shared.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISwitch
import platform.UIKit.UIView
import platform.UIKit.accessibilityLabel

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier,
    enabled: Boolean,
) {
    val onChange by rememberUpdatedState(onCheckedChange)
    UIKitView(
        factory = {
            NativeToggleView { onChange(it) }
        },
        update = {
            if (it.control.on != checked) it.control.setOn(checked, animated = true)
            it.control.enabled = enabled
            it.control.accessibilityLabel = contentDescription
        },
        modifier = modifier.size(64.dp, 48.dp),
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
    )
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class NativeToggleView(onChange: (Boolean) -> Unit) : UIView(frame = CGRectMake(0.0, 0.0, 64.0, 48.0)) {
    val control = UISwitch().apply {
        addAction(UIAction.actionWithHandler { onChange(on) }, UIControlEventValueChanged)
    }

    init {
        backgroundColor = UIColor.clearColor
        addSubview(control)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        control.sizeToFit()
        val width = control.bounds.useContents { size.width }
        val height = control.bounds.useContents { size.height }
        bounds.useContents {
            control.setFrame(CGRectMake((size.width - width) / 2, (size.height - height) / 2, width, height))
        }
    }
}
