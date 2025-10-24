package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Node
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.rotate

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
    enableTest: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val roundCornerShape = RoundedCornerShape(32.dp)
    val context = LocalContext.current
    val delayColor = when {
        delayMs < 0 -> Color.Transparent
        delayMs < 300 -> Color.Green
        delayMs < 900 -> Color(0xFFFFAA00)
        else -> Color.Red
    }
    Surface(
        color = backgroundColor,
        tonalElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = roundCornerShape,
        onClick = {onChoose()},
        border = if (selected) BorderStroke(width = 2.dp, color = Color(0xFF00BFFF)) else null
    ) {
        Row(
            modifier = modifier.fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = modifier
                        .size((screenWidth*0.1).dp.coerceIn(24.dp,48.dp)) // 整体大小
                        .clip(CircleShape) // 裁剪成圆形
                        .background(Color.Blue), // 背景色
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = node.remark?:node.address,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.basicMarquee()
                    )
                    Row {
                        Text(
                            text = node.protocol.name,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (delayMs > 0 ) {
                            Text(
                                text = delayMs.toString(),
                                fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                                color = delayColor,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
            if (onShare != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size((screenWidth*0.1).dp.coerceIn(24.dp,48.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "share"
                    )
                }
            }
            if (delete != null) {
                IconButton(
                    onClick = {
                        delete()
                    } ,
                    modifier.size((screenWidth*0.1).dp.coerceIn(24.dp,48.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "delete"
                    )
                }
            }
            if (onEdit != null) {
                IconButton(
                    onClick = {
                        onEdit.invoke()
                    },
                    modifier.size((screenWidth*0.1).dp.coerceIn(24.dp,48.dp))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = "",
                        modifier = Modifier.fillMaxSize(0.5f)
                    )
                }
            }

            if (onTest != null) {
                val infiniteTransition = rememberInfiniteTransition()
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = if (testing) 360f else 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing)
                    )
                )
                IconButton(
                    onClick = {
                        onTest.invoke()
                    },
                    enabled = enableTest,
                    modifier = modifier.size((screenWidth*0.1).dp.coerceIn(24.dp,48.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "",
                        tint = if (enableTest) MaterialTheme.colorScheme.onBackground
                        else Color.Gray,
                        modifier = Modifier.fillMaxSize(0.5f)
                            .rotate(angle)
                    )
                }
            }

        }
    }
}


@Composable
fun DashboardCard() {
    Card(
        shape = RoundedCornerShape(32),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "star"
            )
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
