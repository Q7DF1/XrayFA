package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
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
                modifier = Modifier.shadow(4.dp)
            )
        }
    ) { innerPadding ->
        when(protocol) {
            Protocol.VLESS.protocolType -> VLESSConfigScreen(innerPadding, content, detailViewmodel)
            Protocol.VMESS.protocolType -> VMESSConfigScreen(innerPadding,content,detailViewmodel)
            Protocol.TROJAN.protocolType -> TROJANConfigScreen(innerPadding,content,detailViewmodel)
            Protocol.SHADOW_SOCKS.protocolType -> SHADOWSOCKSConfigScreen(innerPadding,content,detailViewmodel)
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
    modifier: Modifier = Modifier,
    readOnly: Boolean = true // description all this component
) {

    var expanded by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(field) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (!readOnly) expanded = !expanded
        },
        modifier = modifier,
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
    val vlessConfig by remember { mutableStateOf(detailViewmodel.parseVLESSProtocol(content))}
    var vlessParamMapState =
        rememberSaveable { mutableStateOf<Map<String,String>>(vlessConfig.param) }
    val vlessParamMap by vlessParamMapState
    var address by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vlessConfig.server))
    }
    var port by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vlessConfig.port.toString()))
    }

    var id by
    rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vlessConfig.uuid))
    }
    Box(
        modifier = Modifier.padding(innerPadding)
            .fillMaxSize()
    ) {
        LazyColumn (
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = {address = it},
                    label = { Text("ip") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
            }
            item {

                SelectField(
                    title = "protocol",
                    field = vlessConfig.protocol.name,
                    fieldList = listOf("vless", "vmess","trojan","shadowsocks"),
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
            }
            item {
                OutlinedTextField(
                    value = port,
                    onValueChange = {port = it},
                    label = { Text("port") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
            }
            item {
                OutlinedTextField(
                    value = id,
                    onValueChange = {id = it},
                    label = {Text("id")},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
            }
            items(items = vlessParamMap.keys.toList()) { key ->
                OutlinedTextField(
                    value = vlessParamMap[key]?:"",
                    onValueChange = { value ->
                        vlessParamMapState.update(key,value)
                    },
                    label = { Text(key) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * update Map when value changed
 */
fun <K,V>MutableState<Map<K, V>>.update(key: K, value: V) {
    this.value = this.value.toMutableMap().apply {
        this[key] = value
    }
}



@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
    ) {
        Button(
            onClick = {

            },
            modifier = Modifier.weight(1f)
                .padding(horizontal = (screenWidth * 0.08).dp),
            colors = ButtonColors(
                containerColor = Color(0xFF00BFFF),
                contentColor = MaterialTheme.colorScheme.background,
                disabledContentColor = Color.Gray,
                disabledContainerColor = Color.White
            )
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            onClick = {
            },
            modifier = Modifier.weight(1f)
                .padding(horizontal = (screenWidth * 0.08).dp),
            colors = ButtonColors(
                containerColor = Color(0xFF00BFFF),
                contentColor = MaterialTheme.colorScheme.background,
                disabledContentColor = Color.Gray,
                disabledContainerColor = Color.White
            )
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
fun VMESSConfigScreen(
    innerPadding: PaddingValues,
    content:String,
    detailViewmodel: DetailViewmodel,
) {
    val vmess = detailViewmodel.parseVMESSProtocol(content)
    val vmessParamMap = vmess.others.asMap()
    LazyColumn(
        modifier = Modifier.padding(innerPadding)
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(items = vmessParamMap.toList()) { (key,value) ->
            val field = value.asString
            var enable = true
            if (field.isEmpty()) {
                enable = false
            }
            OutlinedTextField(
                value = field,
                onValueChange = {},
                label = { Text(key) },
                readOnly = true,
                enabled = enable,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TROJANConfigScreen(
    innerPadding: PaddingValues,
    content:String,
    detailViewmodel: DetailViewmodel,
) {
    val trojan = detailViewmodel.parseTrojanProtocol(content)
    LazyColumn(
     modifier = Modifier.padding(innerPadding)
         .fillMaxSize()
         .padding(horizontal = 16.dp)
    ) {

        item {
            OutlinedTextField(
                value = trojan.host?:"",
                onValueChange = {},
                label = { Text("host") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }
        item {

            SelectField(
                title = "protocol",
                field = trojan.scheme,
                fieldList = listOf("vless", "vmess","trojan","shadowsocks"),
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }
        item {
            OutlinedTextField(
                value = trojan.port.toString(),
                onValueChange = {},
                label = { Text("port") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }
        item {
            OutlinedTextField(
                value = trojan.password,
                onValueChange = {},
                label = {Text("password")},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }
        items(items = trojan.params.toList()) { (key,value) ->

            OutlinedTextField(
                value = value,
                onValueChange = {},
                label = {Text(key)},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }
    }
}

@Composable
fun SHADOWSOCKSConfigScreen(
    innerPadding: PaddingValues,
    content:String,
    detailViewmodel: DetailViewmodel,
) {
    val shadowSocks = detailViewmodel.parseShadowSocks(content)
    LazyColumn(
        modifier = Modifier.padding(innerPadding)
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {

            OutlinedTextField(
                value = shadowSocks.server,
                onValueChange = {},
                label = {Text("server")},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }

        item {
            OutlinedTextField(
                value = shadowSocks.port.toString(),
                onValueChange = {},
                label = {Text("port")},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }

        item {
            OutlinedTextField(
                value = shadowSocks.password,
                onValueChange = {},
                label = {Text("password")},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }

        item {
            OutlinedTextField(
                value = shadowSocks.tag?:"",
                onValueChange = {},
                label = {Text("tag")},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }

        item {
            OutlinedTextField(
                value = shadowSocks.method,
                onValueChange = {},
                label = {Text("method")},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true
            )
        }
    }
}