package com.android.xrayfa.shared.ui.transitions

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 作用域经 CompositionLocal 下传，而非参数透传：多个转场的起点要穿过 `ChildPages` 的 `PagerScope`
 * 再套两层 Screen 函数，透传会污染七八个函数签名。
 *
 * 用 `staticCompositionLocalOf`（与 `LocalPlatformRootHooks` 一致）：值变化时整棵子树重组，
 * 这让 [Modifier.sharedContainer] 里的条件式 `remember` 是安全的。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Decompose `ChildStack` 交出的作用域。3.5.0 的 `StackAnimationScope` 继承自本类型。 */
val LocalStackAnimationScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
