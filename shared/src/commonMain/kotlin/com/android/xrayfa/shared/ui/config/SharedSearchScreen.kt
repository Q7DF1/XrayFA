package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

    // 退出时补交。弹栈会销毁本屏并取消下面的防抖，不满 300ms 的最后几个按键就丢了，
    // Config 列表会被一个残缺的查询词过滤。必须在事件时刻同步记录，不能用
    // rememberUpdatedState —— 子节点被弹出时未必还会重组一次。
    val queryOnExit = remember { mutableStateOf(query) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest { component.onSearch(it) }
    }

    DisposableEffect(component) {
        onDispose {
            // onSearch 会重查数据库，值没变就别白跑一趟。
            if (queryOnExit.value != component.state.value.searchQuery) {
                component.onSearch(queryOnExit.value)
            }
        }
    }

    SharedSearchChrome(
        query = query,
        onQueryChange = {
            query = it
            queryOnExit.value = it
        },
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
                    queryOnExit.value = ""
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
