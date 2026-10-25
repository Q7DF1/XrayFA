package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun HomeTrafficStatusCard(
    isConnected: Boolean,
    uploadSpeedKbps: Double,
    downloadSpeedKbps: Double,
    uploadLabel: String,
    downloadLabel: String,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val uploadAccent = if (darkTheme) Color(0xFF54DCCB) else Color(0xFF087F79)
    val downloadAccent = if (darkTheme) Color(0xFF76A9FF) else Color(0xFF426EBD)
    val body: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = if (framed) 20.dp else 4.dp, vertical = if (framed) 18.dp else 4.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTrafficSpeedItem(
                modifier = Modifier.weight(1f),
                label = uploadLabel,
                speedKbps = if (isConnected) uploadSpeedKbps else 0.0,
                icon = Icons.Default.KeyboardArrowUp,
                color = uploadAccent,
            )
            VerticalDivider(modifier = Modifier.height(36.dp).width(1.dp))
            HomeTrafficSpeedItem(
                modifier = Modifier.weight(1f),
                label = downloadLabel,
                speedKbps = if (isConnected) downloadSpeedKbps else 0.0,
                icon = Icons.Default.KeyboardArrowDown,
                color = downloadAccent,
                showSparkline = true,
            )
        }
    }
    if (framed) {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            body()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) { body() }
    }
}

@Composable
private fun HomeTrafficSpeedItem(
    label: String,
    speedKbps: Double,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    showSparkline: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.replaceFirstChar { it.uppercaseChar() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showSparkline) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                    TrafficSparkline(color = color)
                }
            }
            Text(
                text = "${formatSpeedKbps(speedKbps)} KB/s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TrafficSparkline(color: Color) {
    Canvas(modifier = Modifier.size(width = 26.dp, height = 12.dp)) {
        val points = listOf(
            Offset(0f, size.height * 0.66f),
            Offset(size.width * 0.20f, size.height * 0.66f),
            Offset(size.width * 0.34f, size.height * 0.25f),
            Offset(size.width * 0.49f, size.height * 0.86f),
            Offset(size.width * 0.64f, size.height * 0.42f),
            Offset(size.width * 0.78f, size.height * 0.58f),
            Offset(size.width, size.height * 0.58f),
        )
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 1.2.dp.toPx()))
    }
}

private fun formatSpeedKbps(speedKbps: Double): String {
    val rounded = (speedKbps * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}
