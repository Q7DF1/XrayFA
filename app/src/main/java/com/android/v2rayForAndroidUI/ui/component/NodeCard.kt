package com.android.v2rayForAndroidUI.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.v2rayForAndroidUI.model.Node

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun NodeCard(
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    node: Node,
    modifier: Modifier = Modifier,
    delete: (() -> Unit)? = null,
    onChoose: () -> Unit = {},
    onShare: (() -> Unit)? = null,
    selected: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val roundCornerShape = RoundedCornerShape(32.dp)
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
                    Text(
                        text = node.protocol.name,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,

                    )
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
            IconButton(
                onClick = {},
                modifier.size((screenWidth*0.1).dp.coerceIn(24.dp,48.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForward,
                    contentDescription = "",
                    modifier = Modifier.fillMaxSize(0.5f)
                )
            }
        }
    }
}

@Composable
fun AnimateNodeCard(
    visible: Boolean,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    node: Node,
    modifier: Modifier,
    delete: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        NodeCard(
            node = node,
            backgroundColor = backgroundColor,
            delete = delete,
            modifier = modifier
        )
    }
}