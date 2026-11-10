package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape

private data class SharedIconKey(val destination: String)

/** Pairs action icons; crossfade when the destination uses a different glyph. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedIcon(destination: String, crossfadeContent: Boolean = false): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animationScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        val contentState = rememberSharedContentState(SharedIconKey(destination))
        // Above the floating nav (1f) and screen containers (0f). Clip to the icon
        // itself, so it can leave its source container during the transition.
        if (crossfadeContent) {
            this@sharedIcon.sharedBounds(
                sharedContentState = contentState,
                animatedVisibilityScope = animationScope,
                enter = fadeIn(XrayMotion.EffectsFloat),
                exit = fadeOut(XrayMotion.EffectsFloat),
                boundsTransform = XrayMotion.ContainerBounds,
                zIndexInOverlay = 2f,
                clipInOverlayDuringTransition = OverlayClip(RectangleShape),
            )
        } else {
            this@sharedIcon.sharedElement(
                sharedContentState = contentState,
                animatedVisibilityScope = animationScope,
                boundsTransform = XrayMotion.ContainerBounds,
                zIndexInOverlay = 2f,
                clipInOverlayDuringTransition = OverlayClip(RectangleShape),
            )
        }
    }
}
