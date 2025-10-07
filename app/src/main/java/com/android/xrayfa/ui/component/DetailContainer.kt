package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.viewmodel.DetailViewmodel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContainer(
    protocol: String,
    content: String,
    detailViewmodel: DetailViewmodel
) {
    Scaffold(
        topBar = {
            TopAppBar(
            title = { Text("Detail") },
        )}
    ) { innerPadding ->
        when(protocol) {
            Protocol.VLESS.protocolName -> VLESSConfigScreen(innerPadding, content, detailViewmodel)
            Protocol.VMESS.protocolName -> VMESSConfigScreen()
            Protocol.TROJAN.protocolName -> TROJANConfigScreen()
            Protocol.SHADOW_SOCKS.protocolName -> SHADOWSOCKSConfigScreen()
            else -> Text("Unknown protocol")
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectField(
    title:String,
    field: String,
    fieldList: List<String>,
    modifier: Modifier = Modifier
) {

    var expanded by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(field) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {expanded = !expanded},
        modifier = modifier
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {fieldValue = it},
            readOnly = true,
            label = {Text(text = title)},
            trailingIcon = {
                Icon(
                    imageVector =
                        if (expanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                    contentDescription = "",
                )
            },
            modifier = modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable,true)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = {
                expanded = false
            },
        ) {
            fieldList.forEach { field ->
                DropdownMenuItem(
                    text = {Text(text = field)},
                    onClick = {
                        fieldValue = field
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun VLESSConfigScreen(
    innerPadding: PaddingValues,
    content:String,
    detailViewmodel: DetailViewmodel,
) {
    val outbound by remember { mutableStateOf(detailViewmodel.parseVLESSProtocol(content))}
    var address by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(outbound.settings?.vnext?.get(0)?.address?:"unknown"))
    }
    var port by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(outbound.settings?.vnext?.get(0)?.port?.toString()?:"unknown"))
    }

    var id by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(outbound.settings?.vnext?.get(0)?.users?.get(0)?.id?:"unknown"))
    }
    var flow by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(outbound.settings?.vnext?.get(0)?.users?.get(0)?.flow?:"unknown"))
    }

    Box(
        modifier = Modifier.padding(innerPadding)
            .fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(0.7f)
                .align(BiasAlignment(0f,-1f))
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = {address = it},
                label = { Text("ip") },
                modifier = Modifier.fillMaxWidth()
            )

            SelectField(
                title = "protocol",
                field = outbound.protocol?:"unknown",
                fieldList = listOf("vless", "vmess","trojan","shadowsocks"),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = {port = it},
                label = { Text("port") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField( //todo length limit
                value = id,
                onValueChange = {id = it},
                label = {Text("id")},
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            SelectField(
                title = "flow",
                field = flow.text,
                fieldList = listOf("vless", "vmess","trojan","shadowsocks"),
                modifier = Modifier.fillMaxWidth()
            )
        }
        ActionButton(
            modifier = Modifier.align(BiasAlignment(0f,0.8f))
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ActionButton(
    modifier: Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
    ) {
        Button(
            onClick = {},
            modifier = Modifier.weight(1f)
                .padding(horizontal = (screenWidth * 0.08).dp)
        ) {
            Text("cancel") // todo use string
        }
        Button(
            onClick = {},
            modifier = Modifier.weight(1f)
                .padding(horizontal = (screenWidth * 0.08).dp)
        ) {
            Text("save")
        }
    }
}

//TODO: add more protocols
@Composable
fun VMESSConfigScreen() {}

@Composable
fun TROJANConfigScreen() {}

@Composable
fun SHADOWSOCKSConfigScreen() {}