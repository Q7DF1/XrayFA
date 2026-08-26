package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.LogRecordingControls
import com.android.xrayfa.shared.ui.settings.SharedAppLogScreen
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.viewmodel.XrayViewmodel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidLogcatScreen(
    viewmodel: XrayViewmodel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logcat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LogcatScreen(
            viewmodel = viewmodel,
            contentPadding = innerPadding,
        )
    }
}

@Composable
fun LogcatScreen(
    viewmodel: XrayViewmodel,
    sharedTransitionScope: SharedTransitionScope? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val logList by viewmodel.logList.collectAsState()
    val isRecording by viewmodel.isLogcatRecording.collectAsState()
    val duration by viewmodel.logcatDuration.collectAsState()
    val countdown by viewmodel.logcatCountdown.collectAsState()
    val context = LocalContext.current
    val durationLabel =
        when (duration) {
            30L -> stringResource(R.string.logcat_duration_30s)
            60L -> stringResource(R.string.logcat_duration_1m)
            300L -> stringResource(R.string.logcat_duration_5m)
            else -> stringResource(R.string.logcat_duration_infinite)
        }
    val actionLabel =
        if (isRecording) {
            if (duration > 0) {
                stringResource(R.string.logcat_recording, countdown)
            } else {
                stringResource(R.string.logcat_stop)
            }
        } else {
            stringResource(R.string.logcat_start)
        }
    val labels = rememberSettingsUiLabels()
    val baseModifier = Modifier.fillMaxSize().padding(contentPadding)

    val screen: @Composable (Modifier) -> Unit = { screenModifier ->
        SharedAppLogScreen(
            lines = logList,
            labels = labels,
            modifier = screenModifier,
            navigationIcon = {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
            },
            actions = { LogcatActionButton(viewmodel) },
            recording =
                LogRecordingControls(
                    isRecording = isRecording,
                    durationButtonText = "${stringResource(R.string.logcat_duration)}: $durationLabel",
                    actionButtonText = actionLabel,
                    durationOptions =
                        listOf(
                            stringResource(R.string.logcat_duration_30s) to 30L,
                            stringResource(R.string.logcat_duration_1m) to 60L,
                            stringResource(R.string.logcat_duration_5m) to 300L,
                            stringResource(R.string.logcat_duration_infinite) to 0L,
                        ),
                    onSelectDuration = viewmodel::setLogcatDuration,
                    onToggleRecording = {
                        if (isRecording) {
                            viewmodel.stopLogcatRecording()
                        } else {
                            viewmodel.startLogcatRecording(context)
                        }
                    },
                ),
            emptyMessage = stringResource(R.string.no_log_text),
        )
    }

    val sharedScope = sharedTransitionScope
    if (sharedScope != null) {
        with(sharedScope) {
            screen(
                baseModifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = Logcat.route),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                ),
            )
        }
    } else {
        screen(baseModifier)
    }
}
