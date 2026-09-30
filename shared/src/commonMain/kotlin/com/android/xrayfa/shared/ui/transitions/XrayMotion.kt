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
    /**
     * 全屏容器扩张。临界阻尼，避免过冲后回弹；那段回弹会占住输入，看起来像卡住。
     */
    val ContainerBounds: BoundsTransform =
        BoundsTransform { _, _ ->
            spring(
                dampingRatio = 1f,
                stiffness = 800f,
                visibilityThreshold = Rect.VisibilityThreshold,
            )
        }

    /** effects default（1.0 / 1600）—— 透明度与颜色，永不过冲。 */
    val EffectsFloat: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 1f, stiffness = 1600f)

    /** 圆角插值。临界阻尼，避免冲过 0 再弹回来。 */
    val SpatialDp: FiniteAnimationSpec<Dp> =
        spring(dampingRatio = 1f, stiffness = 800f, visibilityThreshold = Dp.VisibilityThreshold)

    /** 底栏滑出。临界阻尼，和容器同时结束。 */
    val SpatialIntOffset: FiniteAnimationSpec<IntOffset> =
        spring(
            dampingRatio = 1f,
            stiffness = 800f,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
}
