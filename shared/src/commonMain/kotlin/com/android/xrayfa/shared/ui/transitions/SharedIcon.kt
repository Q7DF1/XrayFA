package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape

private data class SharedIconKey(val destination: String)

/** Pairs an action icon with its destination, independently of the container reveal. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedIcon(destination: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animationScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        this@sharedIcon.sharedElement(
            sharedContentState = rememberSharedContentState(SharedIconKey(destination)),
            animatedVisibilityScope = animationScope,
            boundsTransform = XrayMotion.ContainerBounds,
            // Above the floating nav (1f) and screen containers (0f).
            zIndexInOverlay = 2f,
            // Clip to the icon itself, so it can leave its source container.
            clipInOverlayDuringTransition = OverlayClip(RectangleShape),
        )
    }
}
