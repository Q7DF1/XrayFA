package com.android.xrayfa.shared.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitViewController
import com.android.xrayfa.shared.ui.nav.FloatingNavSearchChrome
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformGlassFloatingActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    nativeSystemImage: String?,
) {
    val major = UIDevice.currentDevice.systemVersion.substringBefore(".").toIntOrNull() ?: 0
    if (major < 26 || nativeSystemImage == null) {
        FloatingNavSearchChrome(onClick = onClick, modifier = modifier, nativeSystemImage = null, nativeContentDescription = contentDescription) {
            Icon(icon, contentDescription, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    NativeGlassSymbolButton(onClick, nativeSystemImage, contentDescription, modifier)
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
internal fun NativeGlassSymbolButton(onClick: () -> Unit, nativeSystemImage: String, contentDescription: String, modifier: Modifier) {
    val click by rememberUpdatedState(onClick)
    UIKitViewController(
        factory = { NativeGlassActionController { click() } },
        update = { it.update(nativeSystemImage, contentDescription) },
        modifier = modifier.layout { measurable, constraints ->
            val outset = 24.dp.roundToPx()
            val child = measurable.measure(constraints.offset(horizontal = outset * 2, vertical = outset * 2))
            layout(child.width - outset * 2, child.height - outset * 2) { child.place(-outset, -outset) }
        },
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true, placedAsOverlay = true),
    )
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class NativeGlassActionController(onClick: () -> Unit) : UIViewController(nibName = null, bundle = null) {
    private var currentSymbol: String? = null
    private val button = UIButton.buttonWithConfiguration(
        UIButtonConfiguration.glassButtonConfiguration().apply {
            baseForegroundColor = UIColor.labelColor
            preferredSymbolConfigurationForImage = UIImageSymbolConfiguration.configurationWithPointSize(24.0)
        },
        primaryAction = UIAction.actionWithHandler { onClick() },
    )

    init {
        view = UIView().apply { backgroundColor = UIColor.clearColor; opaque = false }
        view.addSubview(button)
    }

    fun update(symbol: String, label: String) {
        if (currentSymbol != symbol) {
            val configuration = button.configuration ?: return
            configuration.image = UIImage.systemImageNamed(symbol)
            button.configuration = configuration
            currentSymbol = symbol
        }
        if (button.accessibilityLabel != label) button.accessibilityLabel = label
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        view.bounds.useContents {
            button.setFrame(CGRectMake(24.0, 24.0, (size.width - 48.0).coerceAtLeast(0.0), (size.height - 48.0).coerceAtLeast(0.0)))
        }
    }
}
