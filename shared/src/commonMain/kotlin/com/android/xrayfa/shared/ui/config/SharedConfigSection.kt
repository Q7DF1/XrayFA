package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.navigation.ConfigFilterIds
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun SharedConfigSection(
    component: ConfigComponent,
    modifier: Modifier = Modifier,
    labels: ConfigUiLabels = ConfigUiLabels(),
    listState: LazyListState = rememberLazyListState(),
    nodeDelayMap: Map<Int, Long> = emptyMap(),
    showFilterBar: Boolean = true,
    filterTrailingContent: @Composable (() -> Unit)? = null,
    onNodeSelected: (Node) -> Unit = { component.onSelectNode(it.id) },
    onShareNode: ((Node) -> Unit)? = null,
    onEditNode: ((Node) -> Unit)? = null,
    onDeleteNode: ((Node) -> Unit)? = null,
    onTestNode: ((Node) -> Unit)? = null,
    enableNodeTest: Boolean = false,
    listModifier: Modifier = Modifier,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection? = null,
    rowModifier: (Node) -> Modifier = { Modifier },
    onEmptyAddClick: (() -> Unit)? = null,
) {
    val state by component.state.subscribeAsState()

    Column(modifier = modifier.fillMaxSize()) {
        if (showFilterBar) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.filters, key = { it.id }) { filter ->
                        FilterChip(
                            selected = state.selectedFilterId == filter.id,
                            onClick = { component.onSelectFilter(filter.id) },
                            label = { Text(filter.label) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            border =
                                FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.selectedFilterId == filter.id,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = Color.Transparent,
                                ),
                        )
                    }
                }
                filterTrailingContent?.invoke()
            }
        }

        if (state.nodes.isEmpty()) {
            SharedConfigEmptyContent(
                modifier = Modifier.weight(1f),
                labels = labels,
                selectedFilterId = state.selectedFilterId,
                onAddClick = onEmptyAddClick,
            )
        } else {
            val scrollModifier =
                if (nestedScrollConnection != null) {
                    listModifier.nestedScroll(nestedScrollConnection)
                } else {
                    listModifier
                }
            LazyColumn(state = listState, modifier = scrollModifier) {
                items(state.nodes, key = { it.id }) { node ->
                    val delayMs = nodeDelayMap[node.id] ?: -1L
                    Column {
                        SharedConfigNodeRow(
                            node = node,
                            labels = labels,
                            modifier = rowModifier(node),
                            selected = node.selected,
                            favorite = node.favorite,
                            delayMs = delayMs,
                            testing = delayMs == -1L && nodeDelayMap.containsKey(node.id),
                            enableTest = enableNodeTest,
                            countryEmoji = node.countryISO,
                            onChoose = { onNodeSelected(node) },
                            onFavorite = {
                                component.onToggleFavorite(node.id, !node.favorite)
                            },
                            onTest = onTestNode?.let { { it(node) } },
                            onShare = onShareNode?.let { { it(node) } },
                            onEdit = onEditNode?.let { { it(node) } },
                            onDelete = onDeleteNode?.let { { it(node) } },
                        )
                        if (node != state.nodes.last()) {
                            HorizontalDivider(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 86.dp, end = 16.dp),
                                thickness = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedConfigEmptyContent(
    labels: ConfigUiLabels,
    selectedFilterId: Int,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Outlined.Subscriptions,
                contentDescription = null,
                modifier =
                    Modifier
                        .padding(30.dp)
                        .fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = labels.emptyTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = labels.emptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (
            onAddClick != null &&
            (selectedFilterId == ConfigFilterIds.SUB_MANUAL || selectedFilterId == ConfigFilterIds.SUB_ALL)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(labels.createConfigLabel)
            }
        }
    }
}
