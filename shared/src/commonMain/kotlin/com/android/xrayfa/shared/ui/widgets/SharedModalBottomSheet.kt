package com.android.xrayfa.shared.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Shared modal sheet: fixed heading/actions, scrollable body, IME/safe-area insets,
 * backdrop/back/close dismissal and a draggable handle. Keep the Dialog host to avoid
 * the CMP/Android Material3 ModalBottomSheet binary mismatch.
 */
@Composable
fun SharedModalBottomSheet(
    onDismissRequest: () -> Unit,
    title: String? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val entrance = remember { Animatable(1f) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    fun dismissSheet() {
        if (closing) return
        closing = true
        scope.launch {
            entrance.animateTo(1f, tween(180))
            latestDismiss()
        }
    }
    LaunchedEffect(Unit) { entrance.animateTo(0f, tween(240)) }
    val closeLabel = stringResource(Res.string.cancel)
    Dialog(
        onDismissRequest = ::dismissSheet,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The dialog owns focus and back handling; tapping outside the sheet dismisses it.
            Box(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = null, indication = null, onClick = ::dismissSheet)
                    .clearAndSetSemantics {},
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().imePadding()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .onSizeChanged { sheetHeight = it.height.toFloat() }
                        .graphicsLayer { translationY = entrance.value * sheetHeight + dragOffset }
                        .semantics { if (title != null) paneTitle = title },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        Box(
                            Modifier.fillMaxWidth().height(32.dp)
                                .pointerInput(threshold) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                                        },
                                        onDragEnd = {
                                            if (dragOffset >= threshold) dismissSheet() else dragOffset = 0f
                                        },
                                        onDragCancel = { dragOffset = 0f },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(32.dp, 4.dp).background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ))
                        }
                        if (title != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(title, Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.headlineSmall)
                                IconButton(onClick = ::dismissSheet) {
                                    Icon(Icons.Default.Close, contentDescription = closeLabel)
                                }
                            }
                        }
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp).padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            content = content,
                        )
                        if (footer != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                content = footer,
                            )
                        }
                    }
                }
            }
        }
    }
}
