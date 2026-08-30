package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.search_clear
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
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

    val results: @Composable ColumnScope.() -> Unit = {
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
    }

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
            trailingIcon =
                if (query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                query = ""
                                onSearchQueryChange("")
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.search_clear))
                        }
                    }
                } else {
                    null
                },
        )
    }

    val bar: @Composable (Modifier) -> Unit = { barModifier ->
        SearchBar(
            inputField = inputField,
            expanded = active,
            onExpandedChange = { active = it },
            modifier = barModifier,
            shape = if (active) SearchBarDefaults.fullScreenShape else CircleShape,
            content = results,
        )
    }

    if (active) {
        Dialog(
            onDismissRequest = { active = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            bar(Modifier.fillMaxSize())
        }
    } else {
        bar(modifier.size(56.dp))
    }
}
