package com.android.xrayfa.shared.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max

/** Window coordinates also work for theme choices hosted in a dropdown popup. */
class ThemeRevealController internal constructor(val change: (Offset, () -> Unit) -> Unit)

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController?> { null }

private class RevealFrame(val image: ImageBitmap, val origin: Offset)

@Composable
fun ThemeReveal(content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var frame by remember { mutableStateOf<RevealFrame?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val colors = rememberUpdatedState(MaterialTheme.colorScheme)
    val background = MaterialTheme.colorScheme.background
    val controller = remember {
        ThemeRevealController { origin, update ->
            if (!capturing && frame == null) {
                capturing = true
                scope.launch {
                    try {
                        val previousColors = colors.value
                        val image = layer.toImageBitmap()
                        progress.snapTo(0f)
                        val pending = RevealFrame(image, origin - position)
                        frame = pending
                        update()
                        // DataStore publishes asynchronously. Keep the old frame until the new theme arrives.
                        val changed = withTimeoutOrNull(1000) {
                            snapshotFlow { colors.value }.first { it != previousColors }
                        }
                        if (changed != null) {
                            withFrameNanos { }
                            progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
                        }
                    } finally {
                        frame = null
                        capturing = false
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalThemeRevealController provides controller) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { position = it.positionInWindow() }) {
            Box(Modifier.fillMaxSize().drawWithContent {
                layer.record {
                    // Include the backdrop in the snapshot so unpainted areas never expose
                    // the window background or the new theme outside the reveal circle.
                    drawRect(background)
                    this@drawWithContent.drawContent()
                }
                drawLayer(layer)
            }) { content() }
            frame?.let { saved ->
                Canvas(Modifier.fillMaxSize()) {
                    val radius = hypot(
                        max(saved.origin.x, size.width - saved.origin.x),
                        max(saved.origin.y, size.height - saved.origin.y),
                    ) * progress.value
                    val circle = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(saved.origin, radius))
                    }
                    clipPath(circle, ClipOp.Difference) { drawImage(saved.image) }
                }
            }
        }
    }
}
