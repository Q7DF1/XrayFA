package com.android.xrayfa.shared.ui.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** Both share hosts use the same preview; closing it leaves the share dialog open. */
@Composable
fun ExpandableShareQrCode(bitmap: ImageBitmap) {
    var expanded by remember(bitmap) { mutableStateOf(false) }
    Image(
        bitmap = bitmap,
        contentDescription = stringResource(Res.string.share_qr_expand),
        filterQuality = FilterQuality.None,
        modifier = Modifier.widthIn(max = 250.dp).fillMaxWidth().aspectRatio(1f).clickable { expanded = true },
    )
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(Color.Black)
                    .clickable { expanded = false }.safeDrawingPadding(),
            ) {
                // Fit both portrait and landscape, leaving room for the close affordance.
                val side = minOf(maxWidth, maxHeight - 112.dp).coerceAtLeast(1.dp)
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(Res.string.share_qr_preview),
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.align(Alignment.Center).size(side)
                        .background(Color.White)
                        .clickable { /* Keep the preview open when touching the code. */ },
                )
                IconButton(
                    onClick = { expanded = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, stringResource(Res.string.share_qr_close), tint = Color.White)
                }
            }
        }
    }
}
