package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 容器变换的统一编排。两端共用同一实现，避免手抄修饰符顺序出错。
 *
 * 修饰符顺序是强制的：
 * - 背景与 `clip` 必须在 `sharedBounds` **之后**，才会跟着元素进入 overlay 渲染
 * - 调用方的尺寸与 padding 修饰符必须加在 `.sharedContainer()` **之后**；加在之前会参与
 *   初始/目标边界的推导，两端不一致就会看到元素"跳"一下
 *
 * 作用域缺失时返回原 `Modifier`：未接入的调用点只是没有动画，不会崩。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(
    destination: String,
    shape: Shape,
    containerColor: Color,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val avScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        this@sharedContainer
            .sharedBounds(
                sharedContentState = rememberSharedContentState(SharedContainerKey(destination)),
                animatedVisibilityScope = avScope,
                enter = fadeIn(XrayMotion.EffectsFloat),
                exit = fadeOut(XrayMotion.EffectsFloat),
                boundsTransform = XrayMotion.ContainerBounds,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
            )
            .background(containerColor, shape)
            .clip(shape)
    }
}

/**
 * 目的地侧的形状：从 [startCorner] 插值到直角。
 *
 * Compose 不支持自动形状插值，这里借 `AnimatedVisibilityScope.transition` 手动驱动。
 * 只用在目的地侧 —— 起点侧传自己的固有形状即可（起点会淡出，不需要变形）。
 */
@Composable
fun rememberDestinationShape(startCorner: Dp): Shape {
    val avScope = LocalStackAnimationScope.current ?: return RectangleShape
    val corner by avScope.transition.animateDp(
        transitionSpec = { XrayMotion.SpatialDp },
        label = "sharedContainerCorner",
    ) { state ->
        if (state == EnterExitState.Visible) 0.dp else startCorner
    }
    return RoundedCornerShape(corner)
}

/**
 * 浮动底栏的 overlay 处理。
 *
 * `renderInSharedTransitionScopeOverlay` 有两个作用：避免被提到 overlay 的共享容器盖住，
 * 以及把底栏从 `Idle` 子节点的 `scale` graphicsLayer 里提出来，免得它跟着整页缩放。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.floatingNavOverlay(): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val avScope = LocalStackAnimationScope.current ?: return this
    return with(sharedScope) {
        this@floatingNavOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
    }.then(
        with(avScope) {
            Modifier.animateEnterExit(
                enter =
                    fadeIn(XrayMotion.EffectsFloat) +
                        slideInVertically(XrayMotion.SpatialIntOffset) { it },
                exit =
                    fadeOut(XrayMotion.EffectsFloat) +
                        slideOutVertically(XrayMotion.SpatialIntOffset) { it },
            )
        },
    )
}
