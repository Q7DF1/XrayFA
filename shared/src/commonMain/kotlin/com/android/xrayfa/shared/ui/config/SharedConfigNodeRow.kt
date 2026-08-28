package com.android.xrayfa.shared.ui.config

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.protocol.protocolPrefixMap

/** Flat list row — visual parity with Android `NodeCard` list mode (roundCorner = false). */
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
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.remark?.takeIf { it.isNotBlank() } ?: node.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            protocolPrefixMap[node.protocolPrefix]?.protocolType
                                ?: labels.unknownProtocolLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (delayMs != -1L && (delayMs > 0 || delayMs == -2L)) {
                        Spacer(Modifier.width(8.dp))
                        ConfigDelayChip(
                            delayMs = delayMs,
                            isTesting = false,
                            timeoutLabel = labels.timeoutLabel,
                            testingLabel = labels.testingLabel,
                        )
                    } else if (onTest != null && (delayMs == -1L || testing)) {
                        Spacer(Modifier.width(8.dp))
                        ConfigDelayChip(
                            delayMs = -1L,
                            isTesting = true,
                            timeoutLabel = labels.timeoutLabel,
                            testingLabel = labels.testingLabel,
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onFavorite != null) {
                    IconButton(
                        onClick = onFavorite,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (favorite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                            contentDescription =
                                if (favorite) {
                                    labels.removeFromFavoritesLabel
                                } else {
                                    labels.addToFavoritesLabel
                                },
                            tint =
                                if (favorite) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
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
                if (onShare != null) {
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = labels.shareLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = labels.editLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = labels.deleteLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigDelayChip(
    delayMs: Long,
    isTesting: Boolean,
    timeoutLabel: String,
    testingLabel: String,
) {
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
                .clip(RoundedCornerShape(6.dp))
                .background(delayColor.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall,
            color = delayColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
