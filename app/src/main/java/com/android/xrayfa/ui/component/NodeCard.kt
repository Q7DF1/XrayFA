package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.protocol.protocolPrefixMap
import com.android.xrayfa.utils.ColorMap

import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.draw.scale

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun NodeCard(
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    node: Node,
    modifier: Modifier = Modifier,
    delete: (() -> Unit)? = null,
    onChoose: () -> Unit = {},
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onTest: (() -> Unit)? = null,
    delayMs: Long = -1,
    testing: Boolean = false,
    selected: Boolean = false,
    enableTest: Boolean = false,
    roundCorner: Boolean = false,
    countryEmoji: String = ""
) {
    val context = LocalContext.current
    val delayColor = when {
        delayMs == -2L -> MaterialTheme.colorScheme.error
        delayMs < 0 -> Color.Transparent
        delayMs < 300 -> Color(0xFF4CAF50)
        delayMs < 900 -> Color(0xFFFFA000)
        else -> Color(0xFFF44336)
    }
    
    val elevation by animateDpAsState(
        targetValue = if (selected) 8.dp else 2.dp,
        label = "elevation"
    )

    ElevatedCard(
        onClick = onChoose,
        modifier = modifier.fillMaxWidth(),
        shape = if (roundCorner) RoundedCornerShape(24.dp) else RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Node Icon / Emoji
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ColorMap.getValue(node.subscriptionId).copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                if (countryEmoji.isNotEmpty()) {
                    Text(text = countryEmoji, style = MaterialTheme.typography.titleLarge)
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Node Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.remark ?: node.address,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = protocolPrefixMap[node.protocolPrefix]?.protocolType ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (delayMs > 0 || delayMs == -2L) {
                        val displayText = if (delayMs == -2L) "Timeout" else "${delayMs}ms"
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(delayColor.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.labelSmall,
                                color = delayColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Actions
            Row {
                if (onTest != null) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    
                    // Pulsing scale animation
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (testing) 1.2f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    
                    // Subtle alpha blinking
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (testing) 0.4f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    IconButton(onClick = onTest, enabled = enableTest) {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = "Test",
                            modifier = Modifier.scale(scale),
                            tint = if (enableTest) {
                                MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                            } else Color.Gray
                        )
                    }
                }
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                }
                if (delete != null) {
                    IconButton(onClick = delete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}


@Composable
fun DashboardCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "star"
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = countryCodeToFlagEmoji("SG")
            )
        }
    }
}

@Composable
@Preview
fun DashboardCardPreview() {
    DashboardCard()
}

fun countryCodeToFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return "🏳️"
    val base = 0x1F1E6 - 'A'.code
    val first = Character.toChars(base + countryCode[0].uppercaseChar().code)
    val second = Character.toChars(base + countryCode[1].uppercaseChar().code)
    return String(first) + String(second)
}
