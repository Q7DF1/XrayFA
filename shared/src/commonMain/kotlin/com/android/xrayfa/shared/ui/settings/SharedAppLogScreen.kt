package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.common.utils.AppLogStore
import com.android.xrayfa.shared.platform.ClipboardWriter
import org.koin.mp.KoinPlatform

data class LogRecordingControls(
    val isRecording: Boolean,
    val durationButtonText: String,
    val actionButtonText: String,
    val durationOptions: List<Pair<String, Long>>,
    val onSelectDuration: (Long) -> Unit,
    val onToggleRecording: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAppLogScreen(
    lines: List<String>,
    labels: SettingsUiLabels = SettingsUiLabels(),
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    recording: LogRecordingControls? = null,
    onClear: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    emptyMessage: String = labels.appLogEmptyMessage,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isEmpty()) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            listState.scrollToItem(lines.lastIndex)
            return@LaunchedEffect
        }
        val lastVisible = visible.last().index
        if (lastVisible >= layoutInfo.totalItemsCount - 5) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(labels.logcatTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    when {
                        navigationIcon != null -> navigationIcon()
                        onBack != null ->
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = labels.cancelLabel,
                                )
                            }
                    }
                },
                actions = actions,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (recording != null) {
                var expanded by remember { mutableStateOf(false) }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            enabled = !recording.isRecording,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(recording.durationButtonText)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            recording.durationOptions.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        recording.onSelectDuration(value)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = recording.onToggleRecording,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (recording.isRecording) {
                                    Icons.Default.Refresh
                                } else {
                                    Icons.Default.PlayArrow
                                },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = recording.actionButtonText,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            if (onClear != null || onCopy != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onClear != null) {
                        OutlinedButton(onClick = onClear) {
                            Text(labels.appLogClearLabel)
                        }
                    }
                    if (onCopy != null) {
                        OutlinedButton(onClick = onCopy, enabled = lines.isNotEmpty()) {
                            Text(labels.appLogCopyLabel)
                        }
                    }
                }
            }

            if (lines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(items = lines, key = { index, line -> "$index:$line" }) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharedInProcessAppLogScreen(
    onBack: () -> Unit,
    labels: SettingsUiLabels = SettingsUiLabels(),
    modifier: Modifier = Modifier,
) {
    val lines by AppLogStore.lines.collectAsState()
    val clipboardWriter = remember { KoinPlatform.getKoin().get<ClipboardWriter>() }
    SharedAppLogScreen(
        lines = lines,
        labels = labels,
        modifier = modifier,
        onBack = onBack,
        onClear = AppLogStore::clear,
        onCopy = {
            val snapshot = AppLogStore.snapshot()
            if (snapshot.isNotBlank()) {
                clipboardWriter.writeText(snapshot)
            }
        },
    )
}
