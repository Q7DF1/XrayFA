package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations
import com.android.xrayfa.shared.ui.transitions.sharedContainer
import com.android.xrayfa.shared.ui.transitions.sharedIcon
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
    onOpenSearch: (() -> Unit)? = null,
    onToggle: ((packageName: String, selected: Boolean) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    isLoading: Boolean = false,
    showPermissionDenied: Boolean = false,
    permissionDeniedContent: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    extraActions: @Composable RowScope.() -> Unit = {},
    leadingContent: (@Composable (SharedAppListItem) -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    SharedListScaffold(
        title = labels.appsInfoTitle,
        scrolledChromeShadowElevation = 4.dp,
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
            if (onOpenSearch != null && !showPermissionDenied) {
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.sharedContainer(
                        destination = TransitionDestinations.APPS_SEARCH,
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = labels.appsSearchLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.sharedIcon(TransitionDestinations.APPS_SEARCH).size(24.dp),
                    )
                }
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
                    SharedAppsList(
                        items = items,
                        listState = listState,
                        readOnly = readOnly,
                        onToggle = onToggle,
                        leadingContent = leadingContent,
                    )
                }
            }
        }
    }
}

/** The main list and search results share selection, spacing and accessibility. */
@Composable
internal fun SharedAppsList(
    items: List<SharedAppListItem>,
    listState: LazyListState = rememberLazyListState(),
    readOnly: Boolean = false,
    onToggle: ((String, Boolean) -> Unit)? = null,
    leadingContent: (@Composable (SharedAppListItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        itemsIndexed(items = items, key = { _, app -> app.packageName }) { index, app ->
            SharedAppRow(app, readOnly, onToggle, leadingContent)
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun SharedAppRow(
    item: SharedAppListItem,
    readOnly: Boolean,
    onToggle: ((String, Boolean) -> Unit)?,
    leadingContent: (@Composable (SharedAppListItem) -> Unit)?,
) {
    val selectable = !readOnly && onToggle != null
    ListItem(
        modifier = Modifier.fillMaxWidth().then(
            if (selectable) {
                Modifier.toggleable(
                    value = item.selected,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(item.packageName, it) },
                )
            } else {
                Modifier
            },
        ),
        headlineContent = {
            Text(
                item.appName.ifBlank { item.packageName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                item.packageName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (leadingContent != null) {
                    leadingContent(item)
                } else {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        },
        trailingContent = if (selectable) {
            { Checkbox(checked = item.selected, onCheckedChange = null) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selectable && item.selected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.background
            },
        ),
    )
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
