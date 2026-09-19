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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.ui.chrome.SharedSearchChrome
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 全屏搜索目的地。查询状态归 [ConfigComponent] 所有，本屏幕只做输入防抖与结果呈现。
 */
@OptIn(FlowPreview::class)
@Composable
fun SharedSearchScreen(
    component: ConfigComponent,
    labels: ConfigUiLabels,
    backContentDescription: String,
    onBack: () -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    var query by remember { mutableStateOf(state.searchQuery) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { component.onSearch(it) }
    }

    SharedSearchChrome(
        query = query,
        onQueryChange = { query = it },
        searchLabel = labels.searchLabel,
        onImeSearch = {
            focusManager.clearFocus()
            keyboard?.hide()
        },
        onBack = onBack,
        backContentDescription = backContentDescription,
        modifier = modifier,
        results = {
            ConfigSearchOverlayResults(
                searchQuery = state.searchQuery,
                nodes = state.nodes,
                searchNoResultsLabel = labels.searchNoResultsLabel,
                onResultChosen = { node ->
                    onResultChosen(node.id)
                    query = ""
                    component.onSearch("")
                    onBack()
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
                    modifier = Modifier.clickable { onResultChosen(node) },
                )
            }
        }
    }
}
