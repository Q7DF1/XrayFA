package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.android.xrayfa.viewmodel.XrayViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.ui.navigation.Logcat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(
    viewmodel: XrayViewmodel,
    sharedTransitionScope: SharedTransitionScope,
) {
    val logList by viewmodel.logList.collectAsState()
    val isRecording by viewmodel.isLogcatRecording.collectAsState()
    val duration by viewmodel.logcatDuration.collectAsState()
    val countdown by viewmodel.logcatCountdown.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Auto-scroll logic: only scroll to bottom if we are already near the bottom
    LaunchedEffect(logList.size) {
        if (logList.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isNotEmpty()) {
                val lastVisibleItemIndex = visibleItemsInfo.last().index
                val totalItemsCount = layoutInfo.totalItemsCount
                // If we are within 5 items of the bottom, auto-scroll
                if (lastVisibleItemIndex >= totalItemsCount - 5) {
                    listState.animateScrollToItem(logList.size - 1)
                }
            } else {
                // Initial load
                listState.scrollToItem(logList.size - 1)
            }
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .sharedElement(
                    sharedTransitionScope.rememberSharedContentState(key = Logcat.route),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {Text(stringResource(Logcat.title), fontWeight = FontWeight.Bold)},
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = ""
                        )
                    },
                    actions = { LogcatActionButton(viewmodel)},
                    modifier = Modifier.shadow(4.dp)
                )

                // Control Bar
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var expanded by remember { mutableStateOf(false) }

                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                enabled = !isRecording,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "${stringResource(R.string.logcat_duration)}: ${
                                    when(duration) {
                                        30L -> stringResource(R.string.logcat_duration_30s)
                                        60L -> stringResource(R.string.logcat_duration_1m)
                                        300L -> stringResource(R.string.logcat_duration_5m)
                                        else -> stringResource(R.string.logcat_duration_infinite)
                                    }
                                }")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logcat_duration_30s)) },
                                    onClick = { viewmodel.setLogcatDuration(30L); expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logcat_duration_1m)) },
                                    onClick = { viewmodel.setLogcatDuration(60L); expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logcat_duration_5m)) },
                                    onClick = { viewmodel.setLogcatDuration(300L); expanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.logcat_duration_infinite)) },
                                    onClick = { viewmodel.setLogcatDuration(0L); expanded = false }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isRecording) viewmodel.stopLogcatRecording()
                                else viewmodel.startLogcatRecording(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isRecording) {
                                    if (duration > 0) stringResource(R.string.logcat_recording, countdown)
                                    else stringResource(R.string.logcat_stop)
                                } else stringResource(R.string.logcat_start),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                if (logList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = stringResource(R.string.no_log_text),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp)
                    ) {
                        items(items = logList) { logLine->
                            Text(
                                text = logLine,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}