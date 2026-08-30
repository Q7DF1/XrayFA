package com.android.xrayfa.shared.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.xrayfa.model.Node

@Composable
actual fun ActualConfigSearchFab(
    searchQuery: String,
    nodes: List<Node>,
    searchLabel: String,
    searchNoResultsLabel: String,
    onSearch: (String) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier,
) {
    ConfigSearchBarImpl(
        useDocked = false,
        searchQuery = searchQuery,
        nodes = nodes,
        searchLabel = searchLabel,
        searchNoResultsLabel = searchNoResultsLabel,
        onSearch = onSearch,
        onSearchExpanded = onSearchExpanded,
        onResultChosen = onResultChosen,
        modifier = modifier,
    )
}
