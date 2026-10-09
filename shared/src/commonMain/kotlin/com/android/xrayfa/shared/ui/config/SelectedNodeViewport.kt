package com.android.xrayfa.shared.ui.config

internal enum class SelectedNodeViewport {
    Visible,
    Above,
    Below,
    Unknown,
}

internal fun selectedNodeViewport(
    selectedIndex: Int,
    visibleItemIndices: List<Int>,
): SelectedNodeViewport {
    if (selectedIndex < 0 || visibleItemIndices.isEmpty()) {
        return SelectedNodeViewport.Unknown
    }
    return when {
        selectedIndex in visibleItemIndices -> SelectedNodeViewport.Visible
        selectedIndex < visibleItemIndices.first() -> SelectedNodeViewport.Above
        else -> SelectedNodeViewport.Below
    }
}
