package com.android.xrayfa.shared.ui.config

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.android.xrayfa.shared.ui.theme.AppRadius
import com.android.xrayfa.shared.ui.widgets.SharedModalBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.model.requiresXDriveSetup
import com.android.xrayfa.model.isManagedXDrive
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import com.android.xrayfa.model.protocol.protocolPrefixMap

/** Node list row with secondary actions in a dedicated bottom sheet. */
@Composable
fun SharedConfigNodeRow(
    node: Node,
    labels: ConfigUiLabels,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    favorite: Boolean = false,
    delayMs: Long = -1L,
    testing: Boolean = false,
    enableTest: Boolean = false,
    countryEmoji: String = "",
    onChoose: () -> Unit = {},
    onFavorite: (() -> Unit)? = null,
    onTest: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var showActions by remember(node.id) { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onChoose),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                        ),
            )
            Spacer(Modifier.width(12.dp))

            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (countryEmoji.isNotEmpty()) {
                    Text(text = countryEmoji, fontSize = 22.sp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (favorite) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).size(18.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Icon(Icons.Filled.Favorite, labels.removeFromFavoritesLabel,
                            modifier = Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.remark?.takeIf { it.isNotBlank() } ?: node.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.heightIn(min = DelayChipHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            if (node.requiresXDriveSetup) stringResource(Res.string.xdrive_pending) else if (node.isManagedXDrive) "vless · xdrive" else if (node.isJsonConfig) stringResource(Res.string.json_config_title) else protocolPrefixMap[node.protocolPrefix]?.protocolType
                                ?: labels.unknownProtocolLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    ConfigDelayChip(
                        delayMs = delayMs,
                        isTesting = testing,
                        timeoutLabel = labels.timeoutLabel,
                        testingLabel = labels.testingLabel,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onTest != null) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by
                        infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (testing) 1.2f else 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "scale",
                        )
                    val alpha by
                        infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (testing) 0.4f else 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "alpha",
                        )
                    IconButton(
                        onClick = onTest,
                        enabled = enableTest,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = labels.testDelayLabel,
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .scale(scale),
                            tint =
                                if (enableTest) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                },
                        )
                    }
                }
                if (onFavorite != null || onShare != null || onEdit != null || onDelete != null) {
                    IconButton(onClick = { showActions = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.MoreHoriz, stringResource(Res.string.node_actions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showActions) {
        SharedModalBottomSheet(onDismissRequest = { showActions = false }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center) {
                    if (countryEmoji.isNotEmpty()) Text(countryEmoji, fontSize = 26.sp)
                    else Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(node.remark?.takeIf { it.isNotBlank() } ?: node.address,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (node.isJsonConfig) stringResource(Res.string.json_config_title)
                        else protocolPrefixMap[node.protocolPrefix]?.protocolType ?: labels.unknownProtocolLabel,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onFavorite != null) {
                    NodeActionTile(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        if (favorite) labels.removeFromFavoritesLabel else labels.addToFavoritesLabel,
                        Modifier.weight(1f), active = favorite,
                    ) { showActions = false; onFavorite() }
                }
                if (onShare != null) NodeActionTile(Icons.Outlined.Share, labels.shareLabel, Modifier.weight(1f)) {
                    showActions = false
                    onShare()
                }
                if (onEdit != null) NodeActionTile(Icons.Outlined.Edit, labels.editLabel, Modifier.weight(1f)) {
                    showActions = false
                    onEdit()
                }
            }
            if (onDelete != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Surface(
                    onClick = { showActions = false; onDelete() },
                    shape = AppRadius.shapeMedium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.error,
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(22.dp))
                        Text(labels.deleteLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = AppRadius.shapeMedium,
        color = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private val DelayChipHeight = 20.dp

@Composable
private fun ConfigDelayChip(
    delayMs: Long,
    isTesting: Boolean,
    timeoutLabel: String,
    testingLabel: String,
) {
    val visible = isTesting || delayMs > 0 || delayMs == -2L
    val delayColor =
        when {
            isTesting -> MaterialTheme.colorScheme.primary
            delayMs == -2L -> MaterialTheme.colorScheme.error
            delayMs < 300 -> Color(0xFF2E7D32)
            delayMs < 900 -> Color(0xFFE65100)
            else -> MaterialTheme.colorScheme.error
        }
    val displayText =
        when {
            isTesting -> testingLabel
            delayMs == -2L -> timeoutLabel
            else -> "${delayMs}ms"
        }

    Box(
        modifier =
            Modifier
                .height(DelayChipHeight)
                .then(
                    if (visible) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(delayColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall,
                color = delayColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
