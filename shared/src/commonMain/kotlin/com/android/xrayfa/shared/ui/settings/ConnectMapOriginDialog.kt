package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.datastore.ConnectMapOrigin
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import com.android.xrayfa.shared.ui.home.HomeNetworkMap
import kotlin.math.roundToInt

@Composable
internal fun ConnectMapOriginDialog(
    origin: ConnectMapOrigin?,
    labels: SettingsUiLabels,
    onSave: (ConnectMapOrigin) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(origin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.mapOriginTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(labels.mapOriginHint)
                HomeNetworkMap(
                    locations = HomeMapLocations(user = selected?.let {
                        HomeMapLocation("", null, null, null, it.latitude, it.longitude)
                    }),
                    fromLabel = labels.mapOriginTitle,
                    toLabel = "",
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f),
                    onLocationSelected = { selected = it },
                )
                Text(selected?.displayCoordinates() ?: labels.mapOriginUnset)
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onSave) }, enabled = selected != null) {
                Text(labels.saveLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancelLabel) } },
    )
}

internal fun ConnectMapOrigin.displayCoordinates(): String =
    "${(latitude * 100).roundToInt() / 100.0}°, ${(longitude * 100).roundToInt() / 100.0}°"
