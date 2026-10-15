package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeFormEditor
import com.android.xrayfa.shared.config.NodeFormIssueKind
import com.android.xrayfa.shared.config.NodeFormOptions
import com.android.xrayfa.shared.config.advancedValue
import com.android.xrayfa.shared.config.selectProtocol
import com.android.xrayfa.shared.config.validationIssue
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.chrome.SharedUnderlineTabBar

@Composable
fun SharedEditScreen(
    nodeId: Int,
    protocol: String?,
    initialContent: String?,
    initialRemark: String?,
    nodeFormEditor: NodeFormEditor,
    onBack: () -> Unit,
    onSave: (NodeEditForm) -> Unit,
    modifier: Modifier = Modifier,
    protocolChangeEnabled: Boolean = nodeId <= 0,
    labels: EditUiLabels = EditUiLabels(),
) {
    var form by remember(nodeId, protocol, initialContent, initialRemark) {
        mutableStateOf(
            nodeFormEditor.parseForm(
                protocol = protocol,
                content = initialContent,
                remark = initialRemark,
            ),
        )
    }

    LaunchedEffect(nodeId, protocol, initialContent, initialRemark) {
        form =
            nodeFormEditor.parseForm(
                protocol = protocol,
                content = initialContent,
                remark = initialRemark,
            )
    }

    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val protocolDrafts = remember(nodeId, protocol, initialContent, initialRemark) { mutableMapOf<Protocol, NodeEditForm>() }
    var attemptedSave by remember(nodeId) { mutableStateOf(false) }
    var buildFailed by remember(form) { mutableStateOf(false) }
    var showAdvanced by remember(nodeId) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val issue = if (attemptedSave) nodeFormEditor.validationIssue(form) else null

    fun validateAndBuild(): Boolean {
        focusManager.clearFocus()
        attemptedSave = true
        if (nodeFormEditor.validationIssue(form) != null) return false
        return runCatching { nodeFormEditor.previewOutbound(form) }
            .onFailure { buildFailed = true }.isSuccess
    }

    LaunchedEffect(attemptedSave, buildFailed) {
        if (attemptedSave && (issue != null || buildFailed)) scrollState.animateScrollTo(0)
    }

    preview?.let { json ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(labels.previewLabel) },
            text = {
                SelectionContainer {
                    Text(
                        json,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text(labels.closeLabel) } },
        )
    }

    SharedListScaffold(
        title = if (nodeId > 0) labels.editTitle else labels.addTitle,
        modifier = modifier,
        largeTitle = false,
        // Keep navigation and Save accessible while editing fields below the fold.
        collapseTitleOnScroll = false,
        titleExpandKey = form.selectedProtocol,
        contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = labels.backContentDescription,
                )
            }
        },
        actions = {
            IconButton(
                onClick = { if (validateAndBuild()) onSave(form) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Done, contentDescription = labels.saveContentDescription)
            }
        },
        footerUnderBar = {
            SharedUnderlineTabBar(
                items = Protocol.entries,
                selectedKey = form.selectedProtocol,
                itemKey = { it },
                onSelect = { selected ->
                    if (selected != form.selectedProtocol) {
                        protocolDrafts[form.selectedProtocol] = form
                        form = protocolDrafts[selected]?.copy(remarks = form.remarks, address = form.address, port = form.port)
                            ?: form.selectProtocol(selected)
                        attemptedSave = false
                    }
                },
                label = { it.name.lowercase() },
                itemEnabled = { protocolChangeEnabled },
            )
        },
    ) { paddingValue ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValue)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            issue?.let {
                val reason = when (it.kind) {
                    NodeFormIssueKind.REQUIRED -> labels.requiredError
                    NodeFormIssueKind.INVALID -> labels.invalidError
                    NodeFormIssueKind.INCOMPATIBLE -> labels.incompatibleError
                    NodeFormIssueKind.SECURITY_REQUIRED -> labels.securityRequiredError
                    NodeFormIssueKind.LEGACY_KCP -> labels.legacyKcpError
                }
                val field = when (it.field) {
                    "address" -> labels.addressLabel
                    "port" -> labels.portLabel
                    "id" -> labels.uuidLabel
                    "password" -> labels.passwordLabel
                    "user" -> labels.usernameOptionalLabel
                    else -> it.field
                }
                Text("$field: $reason", color = MaterialTheme.colorScheme.error)
            }
            if (buildFailed) Text(labels.invalidError, color = MaterialTheme.colorScheme.error)
            Text(
                labels.basicSettingsTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            SharedEditTextField(form.remarks, { form = form.copy(remarks = it) }, labels.remarksLabel)
            SharedEditTextField(form.address, { form = form.copy(address = it) }, labels.addressLabel, isError = issue?.field == "address")
            SharedEditTextField(
                form.port,
                { input -> if (input.all(Char::isDigit) && input.length <= 5) form = form.copy(port = input) },
                labels.portLabel,
                keyboardType = KeyboardType.Number,
                isError = issue?.field == "port",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                labels.protocolSettingsTitle(form.selectedProtocol.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            when (form.selectedProtocol) {
                Protocol.VLESS -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, labels.uuidLabel, isError = issue?.field == "id")
                    SharedEditTextField(
                        form.vlessEncryption,
                        { form = form.copy(vlessEncryption = it) },
                        labels.encryptionLabel,
                    )
                    SharedEditDropdownField(
                        form.flow,
                        { form = form.copy(flow = it) },
                        labels.flowLabel,
                        listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443"),
                        noneOptionLabel = labels.noneOptionLabel,
                    )
                }
                Protocol.VMESS -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, labels.uuidLabel, isError = issue?.field == "id")
                    SharedEditDropdownField(
                        form.vmessSecurity,
                        { form = form.copy(vmessSecurity = it) },
                        labels.securityLabel,
                        listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"),
                        noneOptionLabel = labels.noneOptionLabel,
                    )
                }
                Protocol.SHADOWSOCKS -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, labels.passwordLabel, isPassword = true, isError = issue?.field == "password")
                    SharedEditDropdownField(
                        form.ssMethod,
                        { form = form.copy(ssMethod = it) },
                        labels.methodLabel,
                        NodeFormOptions.methods,
                        noneOptionLabel = labels.noneOptionLabel,
                    )
                }
                Protocol.TROJAN -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, labels.passwordLabel, isPassword = true, isError = issue?.field == "password")
                }
                Protocol.SOCKS -> {
                    SharedEditTextField(form.username, { form = form.copy(username = it) }, labels.usernameOptionalLabel)
                    SharedEditTextField(
                        form.uuidOrPassword,
                        { form = form.copy(uuidOrPassword = it) },
                        labels.passwordOptionalLabel,
                        isPassword = true,
                    )
                }
                Protocol.HTTP -> {
                    SharedEditTextField(form.username, { form = form.copy(username = it) }, labels.usernameOptionalLabel)
                    SharedEditTextField(
                        form.uuidOrPassword,
                        { form = form.copy(uuidOrPassword = it) },
                        labels.passwordOptionalLabel,
                        isPassword = true,
                    )
                }
                Protocol.HYSTERIA2 -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, labels.authLabel, isPassword = true)
                    SharedEditTextField(form.sni, { form = form.copy(sni = it) }, labels.sniLabel)
                    SharedEditTextField(form.hysteria2Alpn, { form = form.copy(hysteria2Alpn = it) }, labels.alpnLabel)
                    SharedEditDropdownField(
                        form.hysteria2Obfs,
                        { form = form.copy(hysteria2Obfs = it) },
                        labels.obfuscationLabel,
                        listOf("", "salamander"),
                        noneOptionLabel = labels.noneOptionLabel,
                    )
                    if (form.hysteria2Obfs.isNotBlank()) {
                        SharedEditTextField(
                            form.hysteria2ObfsPassword,
                            { form = form.copy(hysteria2ObfsPassword = it) },
                            labels.obfuscationPasswordLabel,
                            isPassword = true,
                        )
                    }
                }
            }

            val hasTransportSettings = NodeFormOptions.hasTransport(form.selectedProtocol)
            if (hasTransportSettings) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    labels.transportSettingsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SharedEditDropdownField(
                    form.network,
                    { form = form.copy(network = it, advancedParams = form.advancedParams + mapOf("mode" to if (it == "xhttp") "auto" else "", "headerType" to "none")) },
                    labels.networkLabel,
                    NodeFormOptions.networks,
                    noneOptionLabel = labels.noneOptionLabel,
                )

                if (form.network in setOf("ws", "websocket", "xhttp", "splithttp", "httpupgrade") ||
                    (form.network in setOf("raw", "tcp") && form.advancedValue("headerType") == "http")) {
                    SharedEditTextField(form.wsPath, { form = form.copy(wsPath = it) }, labels.wsPathLabel)
                    SharedEditTextField(form.wsHost, { form = form.copy(wsHost = it) }, labels.wsHostLabel)
                } else if (form.network == "grpc") {
                    SharedEditTextField(
                        form.grpcServiceName,
                        { form = form.copy(grpcServiceName = it) },
                        labels.grpcServiceNameLabel,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SharedEditDropdownField(
                    form.transportSecurity,
                    { form = form.copy(transportSecurity = it) },
                    labels.securityLabel,
                    NodeFormOptions.securities(form.network),
                    noneOptionLabel = labels.noneOptionLabel,
                )

                if (form.transportSecurity == "tls" || form.transportSecurity == "reality") {
                    SharedEditTextField(form.sni, { form = form.copy(sni = it) }, labels.sniServerNameLabel)
                    SharedEditDropdownField(
                        form.fingerprint,
                        { form = form.copy(fingerprint = it) },
                        labels.fingerprintLabel,
                        if (form.transportSecurity == "reality") NodeFormOptions.fingerprints.filter(String::isNotEmpty) else NodeFormOptions.fingerprints,
                        noneOptionLabel = labels.noneOptionLabel,
                    )

                    if (form.transportSecurity == "reality") {
                        SharedEditTextField(form.publicKey, { form = form.copy(publicKey = it) }, labels.publicKeyLabel)
                        SharedEditTextField(form.shortId, { form = form.copy(shortId = it) }, labels.shortIdLabel)
                    }
                }
            }
            val advancedKeys = NodeFormOptions.advancedKeys(form)
            if (advancedKeys.isNotEmpty()) {
                HorizontalDivider()
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAdvanced) labels.hideAdvancedLabel else labels.advancedLabel)
                }
                if (showAdvanced) {
                    advancedKeys.forEach { key ->
                        val fieldLabel = when (key) {
                            "pcs" -> "pinnedPeerCertSha256"
                            "vcn" -> "verifyPeerCertByName"
                            "ech" -> "echConfigList"
                            "spx" -> "spiderX"
                            "pqv" -> "mldsa65Verify"
                            else -> key
                        }
                        val options = NodeFormOptions.advancedOptions(form, key)
                        if (options != null) {
                            SharedEditDropdownField(
                                form.advancedValue(key),
                                { form = form.copy(advancedParams = form.advancedParams + (key to it)) },
                                fieldLabel, options, noneOptionLabel = labels.noneOptionLabel,
                            )
                        } else {
                            SharedEditTextField(
                                form.advancedValue(key),
                                { form = form.copy(advancedParams = form.advancedParams + (key to it)) },
                                fieldLabel,
                                singleLine = key != "extra",
                                keyboardType = if (key in NodeFormOptions.kcpKeys) KeyboardType.Number else KeyboardType.Text,
                                isError = issue?.field == key,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = {
                if (validateAndBuild()) preview = nodeFormEditor.previewOutbound(form)
            }, modifier = Modifier.fillMaxWidth()) { Text(labels.previewLabel) }
        }
    }
}
