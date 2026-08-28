package com.android.xrayfa.shared.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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

@Composable
fun HomeSelectedNodeCard(
    node: Node,
    unknownProtocolLabel: String,
    countryEmoji: String = "",
    delayMs: Long = -1L,
    testing: Boolean = false,
    enableTest: Boolean = false,
    onTest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = 1.dp,
                pressedElevation = 2.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                                ?: unknownProtocolLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (testing) {
                        Spacer(Modifier.width(8.dp))
                        HomeDelayChip(delayMs = -1L, isTesting = true)
                    } else if (delayMs > 0 || delayMs == -2L) {
                        Spacer(Modifier.width(8.dp))
                        HomeDelayChip(delayMs = delayMs, isTesting = false)
                    }
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
                        contentDescription = "Test delay",
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
        }
    }
}

@Composable
fun HomeEmptyNodeCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeDelayChip(
    delayMs: Long,
    isTesting: Boolean = false,
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
            isTesting -> "Testing..."
            delayMs == -2L -> "Timeout"
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
