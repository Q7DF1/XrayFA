package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.android.xrayfa.viewmodel.XrayViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.Logcat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(
    viewmodel: XrayViewmodel
) {
    val logList by viewmodel.logList.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewmodel.getLogcatContent()
    }
        Box(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        ) {
           Column(modifier = Modifier.fillMaxSize()) {
               TopAppBar(
                   title = {Text(stringResource(Logcat.title))},
                   navigationIcon = {
                       Icon(
                           imageVector = Icons.Default.Warning,
                           contentDescription = ""
                       )
                   },
                   actions = { LogcatActionButton(viewmodel)},
                   modifier = Modifier.shadow(4.dp)
               )
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
               }else {
                   LazyColumn(
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