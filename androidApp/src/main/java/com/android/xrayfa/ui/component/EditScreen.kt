package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.ui.config.EditUiLabels
import com.android.xrayfa.shared.ui.config.SharedEditScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@Composable
fun EditScreen(
    nodeId: Int = 0,
    remark: String? = null,
    protocol: String? = null,
    initialContent: String? = null,
    sharedTransitionScope: SharedTransitionScope,
    onBack: () -> Unit = {},
) {
    val nodeFormEditor = remember { KoinPlatform.getKoin().get<NodeFormEditor>() }
    val scope = rememberCoroutineScope()
    val editLabels =
        EditUiLabels(
            editTitle = stringResource(R.string.edit),
            addTitle = stringResource(R.string.edit_add_title),
            backContentDescription = stringResource(R.string.edit_back_cd),
            saveContentDescription = stringResource(R.string.edit_save_cd),
            protocolSectionTitle = stringResource(R.string.edit_protocol_section),
            basicSettingsTitle = stringResource(R.string.edit_basic_settings),
            remarksLabel = stringResource(R.string.edit_remarks),
            addressLabel = stringResource(R.string.edit_address),
            portLabel = stringResource(R.string.edit_port),
            protocolSettingsTitleFormat = stringResource(R.string.edit_protocol_settings),
            uuidLabel = stringResource(R.string.edit_uuid),
            encryptionLabel = stringResource(R.string.edit_encryption),
            flowLabel = stringResource(R.string.edit_flow),
            securityLabel = stringResource(R.string.edit_security),
            passwordLabel = stringResource(R.string.edit_password),
            methodLabel = stringResource(R.string.edit_method),
            usernameOptionalLabel = stringResource(R.string.edit_username_optional),
            passwordOptionalLabel = stringResource(R.string.edit_password_optional),
            authLabel = stringResource(R.string.edit_auth),
            sniLabel = stringResource(R.string.edit_sni),
            alpnLabel = stringResource(R.string.edit_alpn),
            obfuscationLabel = stringResource(R.string.edit_obfuscation),
            obfuscationPasswordLabel = stringResource(R.string.edit_obfuscation_password),
            allowInsecureLabel = stringResource(R.string.edit_allow_insecure),
            transportSettingsTitle = stringResource(R.string.edit_transport_settings),
            networkLabel = stringResource(R.string.edit_network),
            wsPathLabel = stringResource(R.string.edit_ws_path),
            wsHostLabel = stringResource(R.string.edit_ws_host),
            grpcServiceNameLabel = stringResource(R.string.edit_grpc_service_name),
            sniServerNameLabel = stringResource(R.string.edit_sni_server_name),
            fingerprintLabel = stringResource(R.string.edit_fingerprint),
            publicKeyLabel = stringResource(R.string.edit_public_key),
            shortIdLabel = stringResource(R.string.edit_short_id),
            noneOptionLabel = stringResource(R.string.none).lowercase(),
        )

    with(sharedTransitionScope) {
        SharedEditScreen(
            nodeId = nodeId,
            protocol = protocol,
            initialContent = initialContent,
            initialRemark = remark,
            nodeFormEditor = nodeFormEditor,
            onBack = onBack,
            onSave = { form ->
                scope.launch(Dispatchers.IO) {
                    nodeFormEditor.saveForm(nodeId, form)
                }
                onBack()
            },
            labels = editLabels,
            modifier =
                Modifier.sharedElement(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState(key = nodeId),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                ),
        )
    }
}
