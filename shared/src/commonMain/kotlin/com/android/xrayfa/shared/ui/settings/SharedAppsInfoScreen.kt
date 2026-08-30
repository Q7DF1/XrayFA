package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.arkivanov.decompose.extensions.compose.subscribeAsState

data class SharedAppListItem(
    val packageName: String,
    val appName: String,
    val selected: Boolean,
)

@Composable
fun SharedAppsPickerScreen(
    items: List<SharedAppListItem>,
    labels: SettingsUiLabels = SettingsUiLabels(),
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
    onToggle: ((packageName: String, selected: Boolean) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    isLoading: Boolean = false,
    showPermissionDenied: Boolean = false,
    permissionDeniedContent: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    extraActions: @Composable RowScope.() -> Unit = {},
    leadingContent: @Composable (SharedAppListItem) -> Unit = {},
) {
    val listState = rememberLazyListState()

    SharedListScaffold(
        title = labels.appsInfoTitle,
        modifier = modifier.fillMaxSize(),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = labels.cancelLabel,
                    )
                }
            }
        },
        actions = {
            if (onSearchQueryChange != null && !showPermissionDenied) {
                AppsSearchBar(
                    searchQuery = searchQuery,
                    items = items,
                    searchLabel = labels.appsSearchLabel,
                    searchNoResultsLabel = labels.appsNoMatchesMessage,
                    onSearchQueryChange = onSearchQueryChange,
                    onItemChosen = { item ->
                        onToggle?.invoke(item.packageName, !item.selected)
                    },
                )
            }
            if (onClearAll != null && !readOnly) {
                IconButton(onClick = onClearAll) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = labels.appsClearAllLabel,
                    )
                }
            }
            extraActions()
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val emptyMessage =
                appsListEmptyMessage(
                    itemsEmpty = items.isEmpty(),
                    searchQuery = searchQuery,
                    noPackagesMessage = labels.appsNoPackagesMessage,
                    noMatchesMessage = labels.appsNoMatchesMessage,
                )
            when {
                showPermissionDenied && permissionDeniedContent != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        permissionDeniedContent()
                    }
                }
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
                emptyMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(items = items, key = { it.packageName }) { app ->
                            SharedAppRow(
                                item = app,
                                readOnly = readOnly,
                                onToggle = onToggle,
                                leadingContent = leadingContent,
                            )
                            HorizontalDivider(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 48.dp, end = 48.dp),
                                thickness = 1.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedAppRow(
    item: SharedAppListItem,
    readOnly: Boolean,
    onToggle: ((String, Boolean) -> Unit)?,
    leadingContent: @Composable (SharedAppListItem) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (!readOnly && onToggle != null) {
                        Modifier.clickable { onToggle(item.packageName, !item.selected) }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp),
    ) {
        leadingContent(item)
        Text(
            text = item.appName.ifBlank { item.packageName },
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp, horizontal = 8.dp),
        )
        if (readOnly) {
            Text(
                text = item.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        } else if (onToggle != null) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggle(item.packageName, it) },
            )
        }
    }
}

@Composable
fun SharedAppsInfoScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
    labels: SettingsUiLabels = SettingsUiLabels(),
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    SharedAppsPickerScreen(
        items =
            state.allowedPackages.map { packageName ->
                SharedAppListItem(
                    packageName = packageName,
                    appName = packageName,
                    selected = true,
                )
            },
        labels = labels,
        modifier = modifier,
        onBack = onBack,
        readOnly = true,
    )
}
