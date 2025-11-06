package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.android.xrayfa.viewmodel.XrayViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R

@Composable
fun LogcatScreen(
    viewmodel: XrayViewmodel
) {
    val logList by viewmodel.logList.collectAsState()

    LaunchedEffect(Unit) {
        viewmodel.getLogcatContent()
    }
    if (logList.size <= 1) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = stringResource(R.string.no_log_text),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    } else {

        LazyColumn(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
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