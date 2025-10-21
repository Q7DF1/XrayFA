package com.android.xrayfa.ui.component

import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.android.xrayfa.viewmodel.AppsViewmodel
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.viewmodel.AppInfo


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewmodel: AppsViewmodel
) {


    val context = LocalContext.current
    Scaffold(
        topBar = {TopAppBar(
            title = {Text("all app")}
        )}
    ) { paddingValue ->

        val searchAppInfoCompleted by remember { derivedStateOf { viewmodel.searchAppCompleted } }
        val listState = rememberLazyListState()
        LaunchedEffect(Unit) {
            viewmodel.getInstalledPackages(context)
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
                val appInfos = viewmodel.appInfoList
                LazyColumn(
                    contentPadding = paddingValue,
                    state = listState
                ) {
                    items(appInfos) { appInfo ->
                        ApkInfoItem(
                            appName = appInfo.appName,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApkInfoItem(
    appName: String? = "null",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = appName?:"null",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(8f)
        )
        Checkbox(
            checked = false,
            onCheckedChange = { checked ->
            },
            modifier = Modifier.weight(2f)
        )
    }
}


@Composable
@Preview
fun ApkInfoItemPreview() {
    ApkInfoItem(
        "chrome",
    )
}

fun drawableToImageBitmap(drawable: Drawable): androidx.compose.ui.graphics.ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }

    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 48

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    return bitmap.asImageBitmap()
}