package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.xrayfa.shared.ui.chrome.TitleCollapseState
import com.android.xrayfa.shared.ui.chrome.rememberTitleCollapseState

/**
 * Config tab 里必须跨导航存活的 UI 状态。
 *
 * 必须在 `RootContent` 层 remember：`ChildStack` 只组合活跃子节点，Config tab 所在的 `Idle`
 * 子节点在压栈时会被释放。其中 [listState] 是容器回缩动画正确性的硬依赖 —— 若返回时列表跳回顶部，
 * 共享容器的目标边界就是错的，会看到方块飞向屏幕外。
 */
@Stable
internal class ConfigTabChromeState(
    val listState: LazyListState,
    val titleCollapseState: TitleCollapseState,
) {
    var pendingOverlayScroll: OverlayScrollPending? by mutableStateOf(null)
}

@Composable
internal fun rememberConfigTabChromeState(): ConfigTabChromeState {
    val listState = rememberLazyListState()
    val titleCollapseState = rememberTitleCollapseState()
    return remember(listState, titleCollapseState) {
        ConfigTabChromeState(listState = listState, titleCollapseState = titleCollapseState)
    }
}
