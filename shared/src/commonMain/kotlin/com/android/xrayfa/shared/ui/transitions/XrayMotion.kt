package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * M3 Expressive 动效取值，硬编码为 spring 常量。
 *
 * 刻意不使用 `MaterialTheme.motionScheme`：本项目在 commonMain 引用较新的 material3 API 会在
 * Android 上抛 NoSuchMethodError（`androidApp` 同时依赖 androidx Compose BOM，运行时由 androidx
 * material3 胜出，而 `:shared` 按 JB material3 编译）。已有先例见 `SharedModalBottomSheet` 与
 * `SharedSearchChrome` 的注释。本文件只依赖 `androidx.compose.animation.core`。
 *
 * 选 spring 而非 tween：预测返回手势把进度直接喂给同一个 transition，spring 被打断和反向时自然。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
object XrayMotion {
    /** expressive slow spatial（0.8 / 200）—— 全屏级容器扩张。 */
    val ContainerBounds: BoundsTransform =
        BoundsTransform { _, _ ->
            spring(
                dampingRatio = 0.8f,
                stiffness = 200f,
                visibilityThreshold = Rect.VisibilityThreshold,
            )
        }

    /** effects default（1.0 / 1600）—— 透明度与颜色，永不过冲。 */
    val EffectsFloat: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    /** expressive default spatial（0.8 / 380）—— 圆角插值等局部尺寸变化。 */
    val SpatialDp: FiniteAnimationSpec<Dp> =
        spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = Dp.VisibilityThreshold)

    /** expressive default spatial（0.8 / 380）—— 底栏滑出等局部位移。 */
    val SpatialIntOffset: FiniteAnimationSpec<IntOffset> =
        spring(
            dampingRatio = 0.8f,
            stiffness = 380f,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
}
