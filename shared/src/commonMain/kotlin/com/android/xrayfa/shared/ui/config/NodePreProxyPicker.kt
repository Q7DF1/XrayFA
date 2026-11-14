package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.android.xrayfa.config.NodePreProxyPolicy
import com.android.xrayfa.model.Node
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.node_pre_hint
import com.android.xrayfa.shared.resources.node_pre_inherit
import com.android.xrayfa.shared.resources.node_pre_unavailable
import com.android.xrayfa.shared.resources.pre_node
import com.android.xrayfa.shared.ui.widgets.SharedOptionPickerField
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NodePreProxyPicker(
    ownerId: Int,
    selectedNodeId: Int?,
    nodes: List<Node>,
    isXDrive: Boolean,
    noneLabel: String,
    onSelected: (Int?) -> Unit,
) {
    val defaultLabel = if (isXDrive) noneLabel else stringResource(Res.string.node_pre_inherit)
    val valueLabel = when (selectedNodeId) {
        null -> defaultLabel
        NodePreProxyPolicy.DISABLED -> noneLabel
        else -> nodes.find { it.id == selectedNodeId }?.displayName()
            ?: stringResource(Res.string.node_pre_unavailable)
    }
    val options = buildList<Pair<String, Int?>> {
        add(defaultLabel to null)
        if (!isXDrive) add(noneLabel to NodePreProxyPolicy.DISABLED)
        nodes.filter { NodePreProxyPolicy.isEligible(it, ownerId) }.forEach { node ->
            add(node.displayName() to node.id)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SharedOptionPickerField(
            valueLabel = valueLabel,
            label = stringResource(Res.string.pre_node),
            options = options,
            onSelected = onSelected,
            modifier = Modifier.testTag("node-pre-picker"),
        )
        Text(stringResource(Res.string.node_pre_hint), style = MaterialTheme.typography.bodySmall)
    }
}

private fun Node.displayName(): String = remark?.takeIf(String::isNotBlank) ?: address
