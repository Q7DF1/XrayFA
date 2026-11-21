package com.android.xrayfa.shared.ui.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.xrayfa.shared.resources.Res
import com.android.xrayfa.shared.resources.xdrive_no_sharing
import com.android.xrayfa.shared.ui.theme.AppRadius
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShareQrDialog(
    bitmap: ImageBitmap?,
    showStorageHint: Boolean,
    copyLabel: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                shape = AppRadius.shapeLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    bitmap?.let {
                        ExpandableShareQrCode(it)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (showStorageHint) {
                        Text(
                            text = stringResource(Res.string.xdrive_no_sharing),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) {
                        Text(copyLabel)
                    }
                }
            }
        }
    }
}
