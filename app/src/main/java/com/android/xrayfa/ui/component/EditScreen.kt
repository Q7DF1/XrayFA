package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.android.xrayfa.model.protocol.Protocol


import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.ui.LocalNavAnimatedContentScope


import com.android.xrayfa.viewmodel.DetailViewmodel


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    nodeId: Int = 0,
    remark: String? = null,
    protocol: String? = null,
    initialContent: String? = null,
    detailViewmodel: DetailViewmodel,
    sharedTransitionScope: SharedTransitionScope,
    onBack: () -> Unit = {}
) {
    var selectedProtocol by remember { mutableStateOf(Protocol.VLESS) }
    
    // --- Form States ---
    var remarks by remember { mutableStateOf(remark ?: "") }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    
    // Protocol Specific
    var id by remember { mutableStateOf("") } // UUID or Password
    var flow by remember { mutableStateOf("") } 
    var vlessEncryption by remember { mutableStateOf("none") }
    var ssMethod by remember { mutableStateOf("aes-256-gcm") } 
    var vmessSecurity by remember { mutableStateOf("auto") } 

    // Transport Basic
    var network by remember { mutableStateOf("tcp") } 
    var transportSecurity by remember { mutableStateOf("none") } 

    // Transport Advanced - WS
    var wsPath by remember { mutableStateOf("/") }
    var wsHost by remember { mutableStateOf("") }
    
    // Transport Advanced - gRPC
    var grpcServiceName by remember { mutableStateOf("") }

    // Transport Advanced - TLS / Reality
    var sni by remember { mutableStateOf("") }
    var fingerprint by remember { mutableStateOf("chrome") }
    var publicKey by remember { mutableStateOf("") }
    var shortId by remember { mutableStateOf("") }
    
    // Hysteria2
    var hysteria2Obfs by remember { mutableStateOf("") }
    var hysteria2ObfsPassword by remember { mutableStateOf("") }
    var hysteria2Alpn by remember { mutableStateOf("") }
    var hysteria2AllowInsecure by remember { mutableStateOf(false) }

    // Initialize from content if provided
    LaunchedEffect(initialContent) {
        if (!initialContent.isNullOrBlank()) {
            try {
                when (protocol) {
                    Protocol.VLESS.protocolType -> {
                        selectedProtocol = Protocol.VLESS
                        val config = detailViewmodel.parseVLESSProtocol(initialContent)
                        address = config.server
                        port = config.port.toString()
                        id = config.uuid
                        flow = config.param["flow"] ?: ""
                        vlessEncryption = config.param["encryption"] ?: "none"
                        network = config.param["type"] ?: "tcp"
                        transportSecurity = config.param["security"] ?: "none"
                        wsPath = config.param["path"] ?: "/"
                        wsHost = config.param["host"] ?: ""
                        grpcServiceName = config.param["serviceName"] ?: ""
                        sni = config.param["sni"] ?: ""
                        fingerprint = config.param["fp"] ?: "chrome"
                        publicKey = config.param["pbk"] ?: ""
                        shortId = config.param["sid"] ?: ""
                    }
                    Protocol.VMESS.protocolType -> {
                        selectedProtocol = Protocol.VMESS
                        val config = detailViewmodel.parseVMESSProtocol(initialContent)
                        address = config.address
                        val others = config.others
                        port = if (others.has("port")) others.get("port").asString else ""
                        id = config.uuid
                        vmessSecurity = if (others.has("scy")) others.get("scy").asString else "auto"
                        network = config.network
                        transportSecurity = config.tls
                        wsHost = config.host
                        wsPath = if (others.has("path")) others.get("path").asString else "/"
                        sni = if (others.has("sni")) others.get("sni").asString else ""
                        fingerprint = if (others.has("fp")) others.get("fp").asString else "chrome"
                    }
                    Protocol.TROJAN.protocolType -> {
                        selectedProtocol = Protocol.TROJAN
                        val config = detailViewmodel.parseTrojanProtocol(initialContent)
                        address = config.host ?: ""
                        port = config.port?.toString() ?: ""
                        id = config.password
                        network = config.params["type"] ?: "tcp"
                        transportSecurity = config.params["security"] ?: "none"
                        wsPath = config.params["path"] ?: "/"
                        wsHost = config.params["host"] ?: ""
                        grpcServiceName = config.params["serviceName"] ?: ""
                        sni = config.params["sni"] ?: ""
                    }
                    Protocol.SHADOW_SOCKS.protocolType -> {
                        selectedProtocol = Protocol.SHADOW_SOCKS
                        val config = detailViewmodel.parseShadowSocks(initialContent)
                        address = config.server
                        port = config.port.toString()
                        id = config.password
                        ssMethod = config.method
                    }
                    Protocol.HYSTERIA2.protocolType -> {
                        selectedProtocol = Protocol.HYSTERIA2
                        val config = detailViewmodel.parseHysteria2Protocol(initialContent)
                        address = config.address
                        port = config.port.toString()
                        id = config.auth
                        sni = config.param["sni"] ?: ""
                        hysteria2Alpn = config.param["alpn"] ?: ""
                        hysteria2Obfs = config.param["obfs"] ?: ""
                        hysteria2ObfsPassword = config.param["obfs-password"] ?: ""
                        hysteria2AllowInsecure = config.param["allowInsecure"] == "1"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        rememberTopAppBarState()
    )
    val scrollState = rememberScrollState()
    // Observe the overlap fraction to determine if the list is scrolled
    val isScrolled by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    // Animate the shadow elevation for a smooth transition
    val appBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        label = "TopBarShadowElevation"
    )
    with(sharedTransitionScope) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val options = Protocol.entries
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = if (nodeId > 0) "Edit" else "Add")
                            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                items(items = options, key = { it }) { label ->
                                    val isFirst = label == options.first()
                                    val isLast = label == options.last()
                                    ToggleButton(
                                        checked = selectedProtocol == label,
                                        onCheckedChange = { selectedProtocol = label },
                                        enabled = nodeId <= 0, // Protocol usually fixed for existing nodes
                                        shapes = when {
                                            isFirst -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            isLast -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        }
                                    ) {
                                        Text(label.name.lowercase())
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        FloatingActionButton(
                            onClick = {
                                detailViewmodel.saveNode(
                                    nodeId = nodeId,
                                    protocol = selectedProtocol,
                                    remarks = remarks,
                                    address = address,
                                    port = port.toIntOrNull() ?: 0,
                                    uuidOrPassword = id,
                                    flow = flow,
                                    vlessEncryption = vlessEncryption,
                                    vmessSecurity = vmessSecurity,
                                    ssMethod = ssMethod,
                                    network = network,
                                    transportSecurity = transportSecurity,
                                    wsPath = wsPath,
                                    wsHost = wsHost,
                                    grpcServiceName = grpcServiceName,
                                    sni = sni,
                                    fingerprint = fingerprint,
                                    publicKey = publicKey,
                                    shortId = shortId,
                                    hysteria2Obfs = hysteria2Obfs,
                                    hysteria2ObfsPassword = hysteria2ObfsPassword,
                                    hysteria2Alpn = hysteria2Alpn,
                                    hysteria2AllowInsecure = hysteria2AllowInsecure
                                )
                                onBack()
                            },
                            shape = IconButtonDefaults.extraSmallRoundShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(48.dp)
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Filled.Done, "save")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    modifier = Modifier.shadow(appBarElevation)
                )
            },
            modifier = Modifier.sharedElement(
                sharedContentState = sharedTransitionScope.rememberSharedContentState(key = nodeId),
                animatedVisibilityScope = LocalNavAnimatedContentScope.current
            )
        ) { paddingValue ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(paddingValue)
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Basic Settings
                Text("Basic Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                EditTextField(remarks, { remarks = it }, "Remarks")
                EditTextField(address, { address = it }, "Address")
                EditTextField(port, { if (it.all { c -> c.isDigit() }) port = it }, "Port")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 2. Protocol Settings
                Text("${selectedProtocol.name} Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                when (selectedProtocol) {
                    Protocol.VLESS -> {
                        EditTextField(id, { id = it }, "UUID")
                        EditTextField(vlessEncryption, { vlessEncryption = it }, "Encryption (default: none)")
                        EditDropdownField(flow, { flow = it }, "Flow", listOf("", "xtls-rprx-vision"))
                    }
                    Protocol.VMESS -> {
                        EditTextField(id, { id = it }, "UUID")
                        EditDropdownField(vmessSecurity, { vmessSecurity = it }, "Security", listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none"))
                    }
                    Protocol.SHADOW_SOCKS -> {
                        EditTextField(id, { id = it }, "Password")
                        EditDropdownField(ssMethod, { ssMethod = it }, "Method", listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm"))
                    }
                    Protocol.TROJAN -> {
                        EditTextField(id, { id = it }, "Password")
                    }
                    Protocol.HYSTERIA2 -> {
                        EditTextField(id, { id = it }, "Auth")
                        EditTextField(sni, { sni = it }, "SNI")
                        EditTextField(hysteria2Alpn, { hysteria2Alpn = it }, "ALPN")
                        EditDropdownField(
                            hysteria2Obfs,
                            { hysteria2Obfs = it },
                            "Obfuscation",
                            listOf("", "salamander")
                        )
                        if (hysteria2Obfs.isNotBlank()) {
                            EditTextField(
                                hysteria2ObfsPassword,
                                { hysteria2ObfsPassword = it },
                                "Obfuscation Password"
                            )
                        }
                        EditDropdownField(
                            if (hysteria2AllowInsecure) "true" else "false",
                            { hysteria2AllowInsecure = it == "true" },
                            "Allow Insecure",
                            listOf("false", "true")
                        )
                    }
                }

                if (selectedProtocol != Protocol.HYSTERIA2) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 3. Transport Settings
                    Text("Transport Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    EditDropdownField(network, { network = it }, "Network", listOf("tcp", "ws", "grpc", "h2", "quic"))

                    if (network == "ws") {
                        EditTextField(wsPath, { wsPath = it }, "WS Path")
                        EditTextField(wsHost, { wsHost = it }, "WS Host")
                    } else if (network == "grpc") {
                        EditTextField(grpcServiceName, { grpcServiceName = it }, "gRPC Service Name")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    EditDropdownField(transportSecurity, { transportSecurity = it }, "Security", listOf("none", "tls", "reality"))

                    if (transportSecurity == "tls" || transportSecurity == "reality") {
                        EditTextField(sni, { sni = it }, "SNI (Server Name Indication)")
                        EditDropdownField(fingerprint, { fingerprint = it }, "Fingerprint", listOf("chrome", "firefox", "safari", "edge", "android", "ios", "random", "randomized"))

                        if (transportSecurity == "reality") {
                            EditTextField(publicKey, { publicKey = it }, "Public Key")
                            EditTextField(shortId, { shortId = it }, "Short ID")
                        }
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifEmpty { "none" }) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun EditTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun VlessEdit(
    uuid: String,
    onUuidChange: (String) -> Unit,
    flow: String,
    onFlowChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditTextField(value = uuid, onValueChange = onUuidChange, label = "UUID")
        EditTextField(value = flow, onValueChange = onFlowChange, label = "Flow")
    }
}

@Composable
fun VmessEdit(
    uuid: String,
    onUuidChange: (String) -> Unit,
    security: String,
    onSecurityChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditTextField(value = uuid, onValueChange = onUuidChange, label = "UUID")
        EditTextField(value = security, onValueChange = onSecurityChange, label = "Security")
    }
}

@Composable
fun ShadowsocksEdit(
    password: String,
    onPasswordChange: (String) -> Unit,
    method: String,
    onMethodChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EditTextField(value = password, onValueChange = onPasswordChange, label = "Password")
        EditTextField(value = method, onValueChange = onMethodChange, label = "Method")
    }
}

@Composable
fun TrojanEdit(
    password: String,
    onPasswordChange: (String) -> Unit
) {
    EditTextField(value = password, onValueChange = onPasswordChange, label = "Password")
}


@Composable
@Preview(device = "id:pixel_5")
fun EditScreenPreview() {
    // EditScreen() // Needs ViewModel, skip preview or provide mock
}
