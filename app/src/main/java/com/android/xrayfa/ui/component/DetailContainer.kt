package com.android.xrayfa.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.xrayfa.model.Link
import com.android.xrayfa.viewmodel.XrayViewmodel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContainer(
    link: Link? = null,
    xrayViewmodel: XrayViewmodel? = null
) {

    Scaffold(
        topBar = {
            TopAppBar(
            title = { Text("Detail") },
        )}
    ) { innerPadding ->

        var text by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue("example", TextRange(0, 7)))
        }

        Box(
            modifier = Modifier.padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {text = it},
                    label = { Text("label") }
                )

                SelectField(
                    title = "protocol",
                    field = "vless",
                    fieldList = listOf("vless", "vmess","trojan","shadowsocks")
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = {text = it},
                    label = { Text("label") }
                )
            }

        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectField(
    title:String,
    field: String,
    fieldList: List<String>
) {

    var expanded by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(field) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {}
    ) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {fieldValue = it},
            readOnly = true,
            label = {Text(text = title)},
            trailingIcon = {
                Box(
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Icon(
                        imageVector =
                            if (expanded)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                        contentDescription = "",
                    )
                }
            },
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
@Preview
fun DetailContainerPreview() {
    DetailContainer()
}