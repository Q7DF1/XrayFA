package com.android.xrayfa.shared.ui.chrome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun <T> SharedUnderlineTabBar(
    items: List<T>,
    selectedKey: Any?,
    itemKey: (T) -> Any,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    itemEnabled: (T) -> Boolean = { true },
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectedCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val selectedLeftPx =
        run {
            val content = contentCoordinates
            val selected = selectedCoordinates
            if (content != null && selected != null && content.isAttached && selected.isAttached) {
                content.localPositionOf(selected).x
            } else {
                0f
            }
        }
    val selectedWidthPx = selectedCoordinates?.size?.width?.toFloat() ?: 0f
    val indicatorLeft =
        animateDpAsState(
            targetValue = with(density) { selectedLeftPx.toDp() },
            label = "underlineTabIndicatorLeft",
        )
    val indicatorWidth =
        animateDpAsState(
            targetValue = with(density) { selectedWidthPx.toDp() },
            label = "underlineTabIndicatorWidth",
        )
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clipToBounds(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .horizontalScroll(scroll)
                            .onGloballyPositioned { contentCoordinates = it },
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items.forEach { item ->
                                key(itemKey(item)) {
                                    val selected = itemKey(item) == selectedKey
                                    val enabled = itemEnabled(item)
                                    Text(
                                        text = label(item),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color =
                                            when {
                                                selected -> MaterialTheme.colorScheme.primary
                                                enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            },
                                        modifier =
                                            Modifier
                                                .onGloballyPositioned { coordinates ->
                                                    if (selected) {
                                                        selectedCoordinates = coordinates
                                                    }
                                                }
                                                .clickable(
                                                    enabled = enabled,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) { onSelect(item) }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (selectedWidthPx > 0f) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .offset { IntOffset(indicatorLeft.value.roundToPx(), 0) }
                                    .width(indicatorWidth.value)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
            if (trailingContent != null) {
                Box(modifier = Modifier.padding(bottom = 2.dp)) {
                    trailingContent()
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
