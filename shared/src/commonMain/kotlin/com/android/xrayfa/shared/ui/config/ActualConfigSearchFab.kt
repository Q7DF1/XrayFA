package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
expect fun ActualConfigSearchFab(
    searchQuery: String,
    nodes: List<Node>,
    searchLabel: String,
    searchNoResultsLabel: String,
    onSearch: (String) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
internal fun ConfigSearchBarImpl(
    useDocked: Boolean,
    searchQuery: String,
    nodes: List<Node>,
    searchLabel: String,
    searchNoResultsLabel: String,
    onSearch: (String) -> Unit,
    onSearchExpanded: (Boolean) -> Unit,
    onResultChosen: (nodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
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

    val onImeSearch: (String) -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        scope.launch {
            delay(400)
            active = false
        }
    }

    val results: @Composable ColumnScope.() -> Unit = {
        ConfigSearchOverlayResults(
            searchQuery = searchQuery,
            nodes = nodes,
            searchNoResultsLabel = searchNoResultsLabel,
            onResultChosen = { node ->
                query = ""
                onSearch("")
                active = false
                onResultChosen(node.id)
            },
        )
    }

    val barModifier =
        if (active) {
            if (useDocked) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            } else {
                Modifier.fillMaxSize()
            }
        } else {
            modifier.size(56.dp)
        }
    val shape = if (active) SearchBarDefaults.fullScreenShape else CircleShape
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = { query = it },
            onSearch = onImeSearch,
            expanded = active,
            onExpandedChange = { active = it },
            placeholder = { Text(searchLabel) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = searchLabel)
            },
        )
    }

    if (useDocked) {
        DockedSearchBar(
            inputField = inputField,
            expanded = active,
            onExpandedChange = { active = it },
            modifier = barModifier,
            shape = if (active) SearchBarDefaults.dockedShape else CircleShape,
            content = results,
        )
    } else {
        SearchBar(
            inputField = inputField,
            expanded = active,
            onExpandedChange = { active = it },
            modifier = barModifier,
            shape = shape,
            content = results,
        )
    }
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
