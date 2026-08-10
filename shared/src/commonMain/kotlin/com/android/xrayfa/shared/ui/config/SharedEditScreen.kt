package com.android.xrayfa.shared.ui.config

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.shared.config.NodeEditForm
import com.android.xrayfa.shared.config.NodeFormEditor

@OptIn(ExperimentalMaterial3Api::class)
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

    val scrollBehavior =
        TopAppBarDefaults.pinnedScrollBehavior(
            rememberTopAppBarState(),
        )
    val scrollState = rememberScrollState()
    val isScrolled by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    val appBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        label = "TopBarShadowElevation",
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (nodeId > 0) "Edit" else "Add",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave(form) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Filled.Done, contentDescription = "Save")
                    }
                },
                scrollBehavior = scrollBehavior,
                modifier = Modifier.shadow(appBarElevation),
            )
        },
    ) { paddingValue ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValue)
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Protocol",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(Protocol.entries, key = { it.name }) { protocolOption ->
                    FilterChip(
                        selected = form.selectedProtocol == protocolOption,
                        onClick = { form = form.copy(selectedProtocol = protocolOption) },
                        enabled = protocolChangeEnabled,
                        label = { Text(protocolOption.name.lowercase()) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        border =
                            FilterChipDefaults.filterChipBorder(
                                enabled = protocolChangeEnabled,
                                selected = form.selectedProtocol == protocolOption,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = Color.Transparent,
                            ),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Basic Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            SharedEditTextField(form.remarks, { form = form.copy(remarks = it) }, "Remarks")
            SharedEditTextField(form.address, { form = form.copy(address = it) }, "Address")
            SharedEditTextField(
                form.port,
                { input ->
                    if (input.all { c -> c.isDigit() } &&
                        (input.isEmpty() || (input.toIntOrNull()?.let { it in 0..65535 } == true))
                    ) {
                        form = form.copy(port = input)
                    }
                },
                "Port (0-65535)",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "${form.selectedProtocol.name} Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            when (form.selectedProtocol) {
                Protocol.VLESS -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, "UUID")
                    SharedEditTextField(
                        form.vlessEncryption,
                        { form = form.copy(vlessEncryption = it) },
                        "Encryption (default: none)",
                    )
                    SharedEditDropdownField(
                        form.flow,
                        { form = form.copy(flow = it) },
                        "Flow",
                        listOf("", "xtls-rprx-vision"),
                    )
                }
                Protocol.VMESS -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, "UUID")
                    SharedEditDropdownField(
                        form.vmessSecurity,
                        { form = form.copy(vmessSecurity = it) },
                        "Security",
                        listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none"),
                    )
                }
                Protocol.SHADOWSOCKS -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, "Password")
                    SharedEditDropdownField(
                        form.ssMethod,
                        { form = form.copy(ssMethod = it) },
                        "Method",
                        listOf(
                            "aes-256-gcm",
                            "aes-128-gcm",
                            "chacha20-ietf-poly1305",
                            "2022-blake3-aes-128-gcm",
                            "2022-blake3-aes-256-gcm",
                        ),
                    )
                }
                Protocol.TROJAN -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, "Password")
                }
                Protocol.SOCKS -> {
                    SharedEditTextField(form.username, { form = form.copy(username = it) }, "Username (optional)")
                    SharedEditTextField(
                        form.uuidOrPassword,
                        { form = form.copy(uuidOrPassword = it) },
                        "Password (optional)",
                    )
                }
                Protocol.HTTP -> {
                    SharedEditTextField(form.username, { form = form.copy(username = it) }, "Username (optional)")
                    SharedEditTextField(
                        form.uuidOrPassword,
                        { form = form.copy(uuidOrPassword = it) },
                        "Password (optional)",
                    )
                }
                Protocol.HYSTERIA2 -> {
                    SharedEditTextField(form.uuidOrPassword, { form = form.copy(uuidOrPassword = it) }, "Auth")
                    SharedEditTextField(form.sni, { form = form.copy(sni = it) }, "SNI")
                    SharedEditTextField(form.hysteria2Alpn, { form = form.copy(hysteria2Alpn = it) }, "ALPN")
                    SharedEditDropdownField(
                        form.hysteria2Obfs,
                        { form = form.copy(hysteria2Obfs = it) },
                        "Obfuscation",
                        listOf("", "salamander"),
                    )
                    if (form.hysteria2Obfs.isNotBlank()) {
                        SharedEditTextField(
                            form.hysteria2ObfsPassword,
                            { form = form.copy(hysteria2ObfsPassword = it) },
                            "Obfuscation Password",
                        )
                    }
                    SharedEditDropdownField(
                        if (form.allowInsecure) "true" else "false",
                        { form = form.copy(allowInsecure = it == "true") },
                        "Allow Insecure",
                        listOf("false", "true"),
                    )
                }
            }

            val hasTransportSettings =
                form.selectedProtocol != Protocol.HYSTERIA2 &&
                    form.selectedProtocol != Protocol.SOCKS &&
                    form.selectedProtocol != Protocol.HTTP
            if (hasTransportSettings) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Transport Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SharedEditDropdownField(
                    form.network,
                    { form = form.copy(network = it) },
                    "Network",
                    listOf("tcp", "ws", "grpc", "h2", "quic"),
                )

                if (form.network == "ws") {
                    SharedEditTextField(form.wsPath, { form = form.copy(wsPath = it) }, "WS Path")
                    SharedEditTextField(form.wsHost, { form = form.copy(wsHost = it) }, "WS Host")
                } else if (form.network == "grpc") {
                    SharedEditTextField(
                        form.grpcServiceName,
                        { form = form.copy(grpcServiceName = it) },
                        "gRPC Service Name",
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                SharedEditDropdownField(
                    form.transportSecurity,
                    { form = form.copy(transportSecurity = it) },
                    "Security",
                    listOf("none", "tls", "reality"),
                )

                if (form.transportSecurity == "tls" || form.transportSecurity == "reality") {
                    SharedEditTextField(form.sni, { form = form.copy(sni = it) }, "SNI (Server Name Indication)")
                    SharedEditDropdownField(
                        form.fingerprint,
                        { form = form.copy(fingerprint = it) },
                        "Fingerprint",
                        listOf("chrome", "firefox", "safari", "edge", "android", "ios", "random", "randomized"),
                    )

                    if (form.transportSecurity == "reality") {
                        SharedEditTextField(form.publicKey, { form = form.copy(publicKey = it) }, "Public Key")
                        SharedEditTextField(form.shortId, { form = form.copy(shortId = it) }, "Short ID")
                    }

                    if (form.transportSecurity == "tls") {
                        SharedEditDropdownField(
                            if (form.allowInsecure) "true" else "false",
                            { form = form.copy(allowInsecure = it == "true") },
                            "Allow Insecure",
                            listOf("false", "true"),
                        )
                    }
                }
            }
        }
    }
}
