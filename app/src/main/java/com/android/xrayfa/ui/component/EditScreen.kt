package com.android.xrayfa.ui.component

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight


import com.android.xrayfa.viewmodel.DetailViewmodel


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    detailViewmodel: DetailViewmodel,
    onBack: () -> Unit = {}
) {
    var selectedProtocol by remember { mutableStateOf(Protocol.VLESS) }
    
    // --- Form States ---
    var remarks by remember { mutableStateOf("") }
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val options = Protocol.entries
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "Edit")
                        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            items(items = options, key = { it }) { label ->
                                ToggleButton(
                                    checked = selectedProtocol == label,
                                    onCheckedChange = { selectedProtocol = label },
                                    shapes = when (label) {
                                        Protocol.VLESS -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        Protocol.TROJAN -> ButtonGroupDefaults.connectedTrailingButtonShapes()
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
                                protocol = selectedProtocol,
                                remarks = remarks,
                                address = address,
                                port = port.toIntOrNull() ?: 0,
                                id = id,
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
                                shortId = shortId
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
        }
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
                    //todo
                    Text(
                        text = "Not support yet",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
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