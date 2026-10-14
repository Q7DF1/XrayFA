package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.android.xrayfa.common.core.*
import com.android.xrayfa.shared.platform.ClipboardWriter
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

private fun StartupFailureKind.messages(): Pair<StringResource, StringResource> = when (this) {
    StartupFailureKind.NO_NODE -> Res.string.startup_no_node_title to Res.string.startup_no_node_advice
    StartupFailureKind.INVALID_LINK -> Res.string.startup_invalid_link_title to Res.string.startup_invalid_link_advice
    StartupFailureKind.INVALID_JSON -> Res.string.startup_invalid_json_title to Res.string.startup_invalid_json_advice
    StartupFailureKind.UNSUPPORTED_FEATURE -> Res.string.startup_unsupported_feature_title to Res.string.startup_unsupported_feature_advice
    StartupFailureKind.INVALID_PROTOCOL -> Res.string.startup_invalid_protocol_title to Res.string.startup_invalid_protocol_advice
    StartupFailureKind.INVALID_SECURITY -> Res.string.startup_invalid_security_title to Res.string.startup_invalid_security_advice
    StartupFailureKind.GEODATA_MISSING -> Res.string.startup_geodata_missing_title to Res.string.startup_geodata_missing_advice
    StartupFailureKind.GEODATA_INVALID -> Res.string.startup_geodata_invalid_title to Res.string.startup_geodata_invalid_advice
    StartupFailureKind.GEO_CATEGORY_MISSING -> Res.string.startup_geo_category_missing_title to Res.string.startup_geo_category_missing_advice
    StartupFailureKind.INVALID_ROUTING -> Res.string.startup_invalid_routing_title to Res.string.startup_invalid_routing_advice
    StartupFailureKind.INVALID_DNS -> Res.string.startup_invalid_dns_title to Res.string.startup_invalid_dns_advice
    StartupFailureKind.PORT_IN_USE -> Res.string.startup_port_in_use_title to Res.string.startup_port_in_use_advice
    StartupFailureKind.VPN_PERMISSION -> Res.string.startup_vpn_permission_title to Res.string.startup_vpn_permission_advice
    StartupFailureKind.VPN_INTERFACE -> Res.string.startup_vpn_interface_title to Res.string.startup_vpn_interface_advice
    StartupFailureKind.TUN_START -> Res.string.startup_tun_start_title to Res.string.startup_tun_start_advice
    StartupFailureKind.FILE_ACCESS -> Res.string.startup_file_access_title to Res.string.startup_file_access_advice
    StartupFailureKind.CORE_INITIALIZATION -> Res.string.startup_core_initialization_title to Res.string.startup_core_initialization_advice
    StartupFailureKind.UNKNOWN -> Res.string.startup_unknown_title to Res.string.startup_unknown_advice
    StartupFailureKind.NATIVE_LIBRARY_MISSING -> Res.string.startup_native_library_missing_title to Res.string.startup_native_library_missing_advice
    StartupFailureKind.NATIVE_LIBRARY_INCOMPATIBLE -> Res.string.startup_native_library_incompatible_title to Res.string.startup_native_library_incompatible_advice
}

private fun StartupStage.label(): StringResource = when (this) {
    StartupStage.PREPARATION -> Res.string.startup_stage_preparation
    StartupStage.CONFIGURATION -> Res.string.startup_stage_configuration
    StartupStage.GEODATA -> Res.string.startup_stage_geodata
    StartupStage.VPN -> Res.string.startup_stage_vpn
    StartupStage.CORE -> Res.string.startup_stage_core
    StartupStage.TUN -> Res.string.startup_stage_tun
    StartupStage.APPLICATION -> Res.string.startup_stage_application
}

@Composable
internal fun StartupFailureCard(
    failure: VpnStartupFailure,
    busy: Boolean,
    canRestoreGeodata: Boolean,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    var copied by remember(failure) { mutableStateOf(false) }
    val (titleResource, adviceResource) = failure.kind.messages()
    val title = stringResource(titleResource)
    val advice = stringResource(adviceResource)
    val stage = stringResource(failure.stage.label())
    val codeLabel = stringResource(Res.string.startup_code)
    val stageLabel = stringResource(Res.string.startup_stage)
    val subject = failure.subject?.let(::redactStartupDetails)
    val details = redactStartupDetails(failure.technicalDetails)
    val restore = failure.canRestoreGeodata && canRestoreGeodata
    Card(
        modifier = Modifier.fillMaxWidth().testTag("startup_error_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.startup_failed), style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(advice, style = MaterialTheme.typography.bodyMedium)
            subject?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (restore) Text(stringResource(Res.string.startup_restore_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failure.kind != StartupFailureKind.NATIVE_LIBRARY_MISSING &&
                    failure.kind != StartupFailureKind.NATIVE_LIBRARY_INCOMPATIBLE) {
                    TextButton(onClick = if (restore) onRestore else onRetry, enabled = !busy,
                        modifier = Modifier.testTag(if (restore) "startup_error_restore" else "startup_error_retry")) {
                        Text(stringResource(if (restore) Res.string.startup_restore else Res.string.startup_retry))
                    }
                }
                TextButton(onClick = { showDetails = true }, modifier = Modifier.testTag("startup_error_details")) {
                    Text(stringResource(Res.string.startup_details))
                }
            }
        }
    }
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text(stringResource(Res.string.startup_details)) },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text("$codeLabel: ${failure.kind.code}\n$stageLabel: $stage")
                    subject?.let { Text("${stringResource(Res.string.startup_subject)}: $it") }
                    Text(advice)
                    Text(details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showDetails = false }) { Text(stringResource(Res.string.startup_close)) }
        },
        dismissButton = {
            TextButton(onClick = {
                KoinPlatform.getKoin().get<ClipboardWriter>().writeText(
                    listOf("$codeLabel: ${failure.kind.code}", "$stageLabel: $stage", title,
                        advice, subject, details).filterNotNull().joinToString("\n"))
                copied = true
            }, modifier = Modifier.testTag("startup_error_copy")) {
                Text(stringResource(if (copied) Res.string.startup_copied else Res.string.startup_copy))
            }
        },
    )
}
