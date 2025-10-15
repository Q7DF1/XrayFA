package com.android.xrayfa.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.xrayfa.R
import com.android.xrayfa.viewmodel.SettingsViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    viewmodel: SettingsViewmodel,
    modifier: Modifier
) {
    val settingsState by viewmodel.settingsState.collectAsState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsCheckBox(
                title = R.string.enable_ipv6,
                description = R.string.enable_ipv6_description,
                checked = settingsState.ipV6Enable,
                onCheckedChange = { checked->
                    viewmodel.setIpV6Enable(checked)
                }
            )
            SettingsSelectBox(
                title = R.string.dark_mode,
                description = R.string.dark_mode_description,
                onSelected = { mode ->
                    viewmodel.setDarkMode(mode)
                },
                selected = when(settingsState.darkMode) {
                    0 -> stringResource(R.string.light_mode)
                    1 -> stringResource(R.string.dark_mode)
                    2 -> stringResource(R.string.auto_mode)
                    else -> stringResource(R.string.auto_mode)
                },
                options = mapOf(
                    0 to stringResource(R.string.light_mode),
                    1 to stringResource(R.string.dark_mode),
                    2 to stringResource(R.string.auto_mode)
                    )
            )
        }
    }
}

@Composable
fun SettingsCheckBox(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable{},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Box(
            modifier = Modifier.weight(0.2f)
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSelectBox(
    @StringRes title: Int,
    @StringRes description: Int,
    onSelected: (Int) -> Unit = {},
    selected: String = "dark",
    options: Map<Int,String> = mapOf()
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable{},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
            },
            modifier = Modifier.weight(0.2f)
                .padding(end = 8.dp)
        ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        expanded = !expanded
                    }
                ) {
                    Text(
                        text = selected,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = "dark mode"
                    )
                }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {expanded = false}
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.value
                            )
                        },
                        onClick = {
                            onSelected(option.key)
                        }
                    )
                }
            }
        }
    }
}


@Composable
@Preview
fun SettingsSelectBoxPreview() {
    SettingsSelectBox(
        R.string.delete,
        R.string.delete_notify
    )
}

@Composable
@Preview
fun SettingsCheckBoxPreview() {
    SettingsCheckBox(
        R.string.cancel,
        R.string.save
    )
}