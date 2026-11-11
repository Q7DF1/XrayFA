package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.xrayfa.config.XDriveForm
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** No clipboard, QR, preview or export action: storage secrets authorize cloud access. */
@Composable
internal fun XDriveEditorFields(form: XDriveForm, onChange: (XDriveForm) -> Unit, enabled: Boolean) {
    Text(stringResource(Res.string.xdrive_no_sharing), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(Res.string.xdrive_encryption_hint), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(Res.string.xdrive_limits), style = MaterialTheme.typography.bodySmall)
    if (enabled) XDriveEditableFields(form, onChange)
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun XDriveEditableFields(form: XDriveForm, onChange: (XDriveForm) -> Unit) {
    SharedEditDropdownField(form.service, { service ->
        onChange(form.copy(service = service, remoteFolder = "", secrets = listOf("", "", ""), endpoint = "", bucket = "", region = ""))
    }, stringResource(Res.string.xdrive_service), XDriveForm.services)
    SharedEditTextField(form.remoteFolder, { onChange(form.copy(remoteFolder = it)) }, stringResource(when (form.service) {
        "google-drive" -> Res.string.xdrive_folder_id
        "s3" -> Res.string.xdrive_prefix
        else -> Res.string.xdrive_folder
    }))
    if (form.service == "s3") {
        SharedEditTextField(form.endpoint, { onChange(form.copy(endpoint = it)) }, stringResource(Res.string.xdrive_endpoint))
        SharedEditTextField(form.region, { onChange(form.copy(region = it)) }, stringResource(Res.string.xdrive_region))
        SharedEditTextField(form.bucket, { onChange(form.copy(bucket = it)) }, stringResource(Res.string.xdrive_bucket))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.xdrive_path_style))
            Switch(checked = form.pathStyle, onCheckedChange = { onChange(form.copy(pathStyle = it)) })
        }
    }
    val secretLabels = when (form.service) {
        "google-drive" -> listOf(Res.string.xdrive_client_id, Res.string.xdrive_client_secret, Res.string.xdrive_refresh_token)
        "s3" -> listOf(Res.string.xdrive_access_key, Res.string.xdrive_secret_key, Res.string.xdrive_session_token)
        else -> emptyList()
    }
    secretLabels.forEachIndexed { index, label ->
        SharedEditTextField(form.secrets.getOrElse(index) { "" }, { value ->
            val secrets = form.secrets.toMutableList()
            while (secrets.size <= index) secrets.add("")
            secrets[index] = value
            onChange(form.copy(secrets = secrets))
        }, stringResource(label), isPassword = true)
    }
    if (form.service == "template") {
        // Template adapters define their own ordered secret slots.
        SharedEditTextField(form.secrets.joinToString("\n"), {
            onChange(form.copy(secrets = if (it.isEmpty()) emptyList() else it.split('\n')))
        }, stringResource(Res.string.xdrive_template_secrets), isPassword = true, singleLine = false)
    }
    Text(stringResource(Res.string.xdrive_advanced_hint), style = MaterialTheme.typography.bodySmall)
    SharedEditTextField(form.advanced, { onChange(form.copy(advanced = it)) }, stringResource(Res.string.xdrive_advanced), singleLine = false)
}
