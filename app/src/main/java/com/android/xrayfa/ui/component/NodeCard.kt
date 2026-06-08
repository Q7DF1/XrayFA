package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.xrayfa.R
import com.android.xrayfa.dto.Node
import com.android.xrayfa.model.protocol.protocolPrefixMap

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun NodeCard(
    backgroundColor: Color = Color.Unspecified,
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
    favorite: Boolean = false,
    onFavorite: (() -> Unit)? = null,
    enableTest: Boolean = false,
    roundCorner: Boolean = false,
    countryEmoji: String = ""
) {
    if (roundCorner) {
        // Standalone card mode (e.g. HomeScreen): ElevatedCard with rounded corners and subtle shadow
        ElevatedCard(
            onClick = onChoose,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (backgroundColor != Color.Unspecified) backgroundColor
                else MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = 1.dp,
                pressedElevation = 2.dp
            )
        ) {
            NodeCardContent(
                node = node,
                delete = delete,
                onShare = onShare,
                onEdit = onEdit,
                onTest = onTest,
                delayMs = delayMs,
                testing = testing,
                selected = selected,
                favorite = favorite,
                onFavorite = onFavorite,
                enableTest = enableTest,
                countryEmoji = countryEmoji,
                showSelectionIndicator = false,
                contentPadding = 16.dp
            )
        }
    } else {
        // List mode (ConfigScreen): flat Row, selection indicated only by the leading bar
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onChoose)
        ) {
            NodeCardContent(
                node = node,
                delete = delete,
                onShare = onShare,
                onEdit = onEdit,
                onTest = onTest,
                delayMs = delayMs,
                testing = testing,
                selected = selected,
                favorite = favorite,
                onFavorite = onFavorite,
                enableTest = enableTest,
                countryEmoji = countryEmoji,
                showSelectionIndicator = true,
                contentPadding = 12.dp
            )
        }
    }
}

@Composable
private fun NodeCardContent(
    node: Node,
    delete: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onTest: (() -> Unit)?,
    delayMs: Long,
    testing: Boolean,
    selected: Boolean,
    favorite: Boolean,
    onFavorite: (() -> Unit)?,
    enableTest: Boolean,
    countryEmoji: String,
    showSelectionIndicator: Boolean,
    contentPadding: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator bar (list mode only)
        if (showSelectionIndicator) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
            )
            Spacer(Modifier.width(12.dp))
        }

        // Avatar / Emoji
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (countryEmoji.isNotEmpty()) {
                Text(text = countryEmoji, fontSize = 22.sp)
            } else {
                Icon(
                    imageVector = Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Node info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.remark ?: node.address,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = protocolPrefixMap[node.protocolPrefix]?.protocolType
                        ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (delayMs > 0 || delayMs == -2L) {
                    Spacer(Modifier.width(8.dp))
                    DelayChip(delayMs = delayMs)
                }
            }
        }

        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onFavorite != null) {
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (favorite)
                            stringResource(R.string.remove_from_favorites)
                        else stringResource(R.string.add_to_favorites),
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (onTest != null) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (testing) 1.2f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (testing) 0.4f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                IconButton(
                    onClick = onTest,
                    enabled = enableTest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = stringResource(R.string.test_url),
                        modifier = Modifier
                            .size(20.dp)
                            .scale(scale),
                        tint = if (enableTest)
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
            if (onShare != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.clipboard_export),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (delete != null) {
                IconButton(
                    onClick = delete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DelayChip(delayMs: Long) {
    // Delay semantic colors: keep universal green/orange/red semantics, but tone down to Material-friendly saturation
    val delayColor = when {
        delayMs == -2L -> MaterialTheme.colorScheme.error
        delayMs < 300 -> Color(0xFF2E7D32) // green 800
        delayMs < 900 -> Color(0xFFE65100) // orange 900
        else -> MaterialTheme.colorScheme.error
    }
    val displayText = if (delayMs == -2L)
        stringResource(R.string.timeout)
    else "${delayMs}ms"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(delayColor.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall,
            color = delayColor,
            fontWeight = FontWeight.SemiBold
        )
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
