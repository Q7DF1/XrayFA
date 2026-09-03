package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.ui.chrome.SharedSearchChrome
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ActualConfigSearchFab(
    searchQuery: String,
    nodes: List<Node>,
    searchLabel: String,
    searchNoResultsLabel: String,
    onSearch: (String) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    forceCollapsed: Boolean = false,
) {
    ConfigSearchBarImpl(
        searchQuery = searchQuery,
        nodes = nodes,
        searchLabel = searchLabel,
        searchNoResultsLabel = searchNoResultsLabel,
        onSearch = onSearch,
        onSearchExpanded = onSearchExpanded,
        onResultChosen = onResultChosen,
        modifier = modifier,
        forceCollapsed = forceCollapsed,
    )
}

@OptIn(FlowPreview::class)
@Composable
internal fun ConfigSearchBarImpl(
    searchQuery: String,
    nodes: List<Node>,
    searchLabel: String,
    searchNoResultsLabel: String,
    onSearch: (String) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    forceCollapsed: Boolean = false,
) {
    var query by remember { mutableStateOf(searchQuery) }
    var active by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { onSearch(it) }
    }

    LaunchedEffect(active) {
        onSearchExpanded(active)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty() && query.isNotEmpty()) {
            query = ""
        }
    }

    LaunchedEffect(forceCollapsed) {
        if (forceCollapsed) {
            active = false
        }
    }

    val onImeSearch: (String) -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch {
            delay(400)
            active = false
        }
    }

    SharedSearchChrome(
        query = query,
        onQueryChange = { query = it },
        expanded = active,
        onExpandedChange = { active = it },
        searchLabel = searchLabel,
        onImeSearch = onImeSearch,
        modifier = modifier,
        results = {
            ConfigSearchOverlayResults(
                searchQuery = searchQuery,
                nodes = nodes,
                searchNoResultsLabel = searchNoResultsLabel,
                onResultChosen = { node ->
                    onResultChosen(node.id)
                    query = ""
                    onSearch("")
                    active = false
                },
            )
        },
    )
}

@Composable
internal fun ConfigSearchOverlayResults(
    searchQuery: String,
    nodes: List<Node>,
    searchNoResultsLabel: String,
    onResultChosen: (Node) -> Unit,
) {
    if (searchQuery.isNotBlank() && nodes.isEmpty()) {
        Text(searchNoResultsLabel, modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn {
            items(nodes, key = { it.id }) { node ->
                ListItem(
                    headlineContent = { Text(node.remark?.ifBlank { node.url } ?: node.url) },
                    modifier =
                        Modifier.clickable {
                            onResultChosen(node)
                        },
                )
            }
        }
    }
}
