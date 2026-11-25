package com.android.xrayfa.shared.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.connected
import com.android.xrayfa.shared.resources.home_connect_action
import com.android.xrayfa.shared.resources.home_connecting_action
import com.android.xrayfa.shared.resources.home_connection_ready
import com.android.xrayfa.shared.resources.home_disconnect_action
import com.android.xrayfa.shared.resources.home_disconnecting_action
import com.android.xrayfa.shared.ui.theme.AppRadius
import org.jetbrains.compose.resources.stringResource

/** A single, labelled connection action. Busy state never implies a successful connection. */
@Composable
fun HomeConnectButton(
    isConnected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    nodeLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val background by animateColorAsState(
        when {
            isConnected -> colors.primary
            dark -> colors.secondaryContainer
            else -> colors.secondary
        },
        label = "connectionBackground",
    )
    val foreground by animateColorAsState(
        when {
            isConnected -> colors.onPrimary
            dark -> colors.onSecondaryContainer
            else -> colors.onSecondary
        },
        label = "connectionForeground",
    )
    val contentColor = if (!enabled && !busy) colors.onSurfaceVariant else foreground
    val title = stringResource(
        when {
            busy && isConnected -> Res.string.home_disconnecting_action
            busy -> Res.string.home_connecting_action
            isConnected -> Res.string.home_disconnect_action
            else -> Res.string.home_connect_action
        },
    )
    val status = stringResource(if (isConnected) Res.string.connected else Res.string.home_connection_ready)
    val subtitle = nodeLabel?.takeIf { it.isNotBlank() }?.let { "$status · $it" } ?: status
    val shape = AppRadius.shapeLarge

    Button(
        onClick = onToggle,
        enabled = enabled && !busy,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = if (busy) background else colors.surfaceContainerHighest,
            disabledContentColor = if (busy) foreground else colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(start = 11.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 68.dp)
            .clip(shape)
            .then(if (isConnected && !busy && enabled) connectionFlow(foreground) else Modifier)
            .testTag("home-connect-button"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(43.dp).clip(AppRadius.shapeMedium)
                    .background(contentColor.copy(alpha = 0.045f))
                    .border(1.dp, contentColor.copy(alpha = 0.035f), AppRadius.shapeMedium),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(21.dp), color = contentColor, strokeWidth = 1.6.dp)
                } else {
                    Icon(
                        if (isConnected) Icons.Filled.Check else Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun connectionFlow(color: Color): Modifier {
    val transition = rememberInfiniteTransition(label = "connectionFlow")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5500, easing = LinearEasing)), label = "flow")
    return Modifier.drawWithContent {
        drawContent()
        val travel = ((progress - 0.12f) / 0.53f).coerceIn(0f, 1f)
        // Size the highlight by button height, not by the much wider content row.
        val width = (size.height * 0.8f).coerceIn(32.dp.toPx(), 64.dp.toPx())
        val center = -width * 2 + travel * (size.width + width * 4)
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.10f), Color.Transparent),
                // The gradient normal slopes down-right, so the visible band slopes /.
                start = Offset(center - width / 2, size.height / 2 - width * 0.3f),
                end = Offset(center + width / 2, size.height / 2 + width * 0.3f),
            ),
        )
    }
}
