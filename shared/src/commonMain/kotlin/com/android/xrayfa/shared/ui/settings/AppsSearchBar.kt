package com.android.xrayfa.shared.ui.settings

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
import com.android.xrayfa.shared.ui.chrome.SharedSearchChrome
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Composable
internal fun AppsSearchBar(
    searchQuery: String,
    items: List<SharedAppListItem>,
    searchLabel: String,
    searchNoResultsLabel: String,
    onSearchQueryChange: (String) -> Unit,
    onItemChosen: (SharedAppListItem) -> Unit,
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
            .collectLatest { onSearchQueryChange(it) }
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

    SharedSearchChrome(
        query = query,
        onQueryChange = { query = it },
        expanded = active,
        onExpandedChange = { active = it },
        searchLabel = searchLabel,
        onImeSearch = onImeSearch,
        modifier = modifier,
        results = {
            if (searchQuery.isNotBlank() && items.isEmpty()) {
                Text(searchNoResultsLabel, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(items, key = { it.packageName }) { item ->
                        ListItem(
                            headlineContent = { Text(item.appName.ifBlank { item.packageName }) },
                            modifier =
                                Modifier.clickable {
                                    onItemChosen(item)
                                    active = false
                                },
                        )
                    }
                }
            }
        },
    )
}
