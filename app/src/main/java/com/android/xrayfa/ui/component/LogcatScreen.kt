package com.android.xrayfa.ui.component

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.android.xrayfa.viewmodel.XrayViewmodel
import androidx.compose.runtime.getValue
@Composable
fun LogcatScreen(
    viewmodel: XrayViewmodel
) {
    val logList by viewmodel.logList.collectAsState()

    LaunchedEffect(Unit) {
        Log.i("1111111", "LogcatScreen: 11111")
        viewmodel.getLogcatContent()
    }
    LazyColumn() {
        items(items = logList) { logLine->
            Text(text = logLine)
        }
    }
}