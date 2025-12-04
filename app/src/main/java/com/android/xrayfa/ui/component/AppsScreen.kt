package com.android.xrayfa.ui.component

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.android.xrayfa.viewmodel.AppsViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewmodel: AppsViewmodel
) {

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text(stringResource(R.string.all_app_settings))},
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "all_app_settings_lab"
                    )
                                 },
                actions = {
                    IconButton(
                        onClick = {
                            viewmodel.setAllowedPackages(emptyList()) {
                                viewmodel.getInstalledPackages(context)
                            }
                        }
                    ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "unselect all app"
                    )
                }
            },
                modifier = Modifier.shadow(4.dp)
        )}
    ) { paddingValue ->

        val searchAppInfoCompleted by remember { derivedStateOf { viewmodel.searchAppCompleted } }
        val listState = rememberLazyListState()
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                viewmodel.getInstalledPackages(context)
            }
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(top = paddingValue.calculateTopPadding())
        ) {

            if (!searchAppInfoCompleted) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }else {
                val appInfos by viewmodel.appInfos.collectAsState()
                LazyColumn(
                    state = listState
                ) {
                    items(appInfos) { appInfo ->
                        ApkInfoItem(
                            appName = appInfo.appName,
                            painter = appInfo.icon,
                            initChecked = appInfo.allow,
                            onCheck = { checked ->
                                if (checked) viewmodel.addAllowPackage(appInfo.packageName)
                                else viewmodel.removeAllowPackage(appInfo.packageName)
                            }

                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApkInfoItem(
    appName: String,
    painter: Painter,
    onCheck: (Boolean) -> Unit,
    initChecked: Boolean
) {
    var checked by remember { mutableStateOf(initChecked) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable {
                checked = !checked
                onCheck(checked)
            }
    ) {
        Image(
            painter = painter,
            contentDescription = "app_icon",
            modifier = Modifier.weight(2f)
                .size(24.dp)
        )
        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(6f)
                .padding(vertical = 16.dp)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
                onCheck(checked)
            },
            modifier = Modifier.weight(2f)
        )
    }
}