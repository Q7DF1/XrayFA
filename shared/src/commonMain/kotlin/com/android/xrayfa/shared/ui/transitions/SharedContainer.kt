package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
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
 * 尺寸用 [SharedTransitionScope.ResizeMode.RemeasureToBounds]：动画中按当前边界重新测量，
 * 文字保持字号，图标保持自身大小，多出来的区域由容器裁切。两端内容靠 [fadeIn] / [fadeOut]
 * 交叉替换。`ScaleToBounds` 会把整段内容当成图片拉伸，日志和齿轮都会被放大。
 *
 * 作用域缺失时返回原 `Modifier`：未接入的调用点只是没有动画，不会崩。
 *
 * Android 运行时必须与 CMP 1.9.3 对齐（`androidx.compose.animation:1.9.4`）。
 * Compose BOM 会把它升到 1.11.x，Kotlin 默认参合成方法对不上，启动即 NoSuchMethodError。
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
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
            .background(containerColor, shape)
            .clip(shape)
    }
}

/**
 * 卡片里第一行、最后一行露出的圆角。
 * 卡片圆角是 24dp，内容上下各内缩 4dp，行上实际能看到的大约是 20dp，而且只有外侧两个角。
 */
val PlatformRowOuterCorner = 20.dp

fun platformRowShape(roundTop: Boolean): Shape =
    if (roundTop) {
        RoundedCornerShape(
            topStart = PlatformRowOuterCorner,
            topEnd = PlatformRowOuterCorner,
        )
    } else {
        RoundedCornerShape(
            bottomStart = PlatformRowOuterCorner,
            bottomEnd = PlatformRowOuterCorner,
        )
    }

/**
 * 目的地侧的形状：全屏时为直角，进出场时收到 [topStart] 等四个角。
 *
 * 可以只圆外侧两个角，这样从卡片第一行、最后一行收回时不会先出现一圈直角。
 */
@Composable
fun rememberDestinationShape(
    startCorner: Dp = 0.dp,
    topStart: Dp = startCorner,
    topEnd: Dp = startCorner,
    bottomEnd: Dp = startCorner,
    bottomStart: Dp = startCorner,
): Shape {
    val avScope = LocalStackAnimationScope.current ?:
        return RoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
        )
    val raw by avScope.transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 1f, stiffness = 800f) },
        label = "sharedContainerCorner",
    ) { state ->
        if (state == EnterExitState.Visible) 0f else 1f
    }
    val fraction = raw.coerceIn(0f, 1f)
    return RoundedCornerShape(
        topStart = topStart * fraction,
        topEnd = topEnd * fraction,
        bottomEnd = bottomEnd * fraction,
        bottomStart = bottomStart * fraction,
    )
}

/**
 * 浮动底栏的 overlay 处理。
 *
 * `renderInSharedTransitionScopeOverlay` 把底栏提到共享转场层，避免被扩张中的容器盖住，
 * 也避免跟着 `Idle` 子节点的 scale 一起缩小。
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
