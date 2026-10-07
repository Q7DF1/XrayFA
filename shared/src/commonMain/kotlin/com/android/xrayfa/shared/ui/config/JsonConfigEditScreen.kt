package com.android.xrayfa.shared.ui.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.config.*
import com.android.xrayfa.model.jsonInboundTag
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.platform.rememberJsonConfigFilePicker
import com.android.xrayfa.shared.resources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform

@Composable
fun jsonConfigErrorText(reason: String): String = stringResource(when (reason) {
    "TOO_LARGE" -> Res.string.json_config_too_large
    "INBOUND_REQUIRED" -> Res.string.json_config_mapping_error
    "CONFLICT" -> Res.string.json_config_conflict
    "EXTERNAL_FILE" -> Res.string.json_config_external_file
    "READ" -> Res.string.json_config_read_error
    else -> Res.string.json_config_invalid
})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonConfigEditScreen(id: Int, component: ConfigComponent, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repository = remember { KoinPlatform.getKoin().get<NodeRepository>() }
    var name by remember(id) { mutableStateOf("") }
    var text by remember(id) { mutableStateOf("") }
    var inboundTag by remember(id) { mutableStateOf<String?>(null) }
    var tags by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var busy by remember(id) { mutableStateOf(false) }
    var loaded by remember(id) { mutableStateOf(id == 0) }
    LaunchedEffect(id) {
        if (id > 0) {
            val node = repository.loadLinksById(id).first()
            if (node?.jsonData != null) {
                name = node.remark.orEmpty()
                text = node.jsonData.orEmpty()
                inboundTag = node.jsonInboundTag
                loaded = true
            } else error = "INVALID_CONFIG"
        }
    }
    LaunchedEffect(text) {
        delay(250)
        tags = withContext(Dispatchers.Default) { runCatching { JsonVpnConfig.inboundTags(text) }.getOrDefault(emptyList()) }
    }
    val pickFile = rememberJsonConfigFilePicker { file, failure ->
        if (failure != null) error = failure
        if (file != null) {
            text = file.text
            if (name.isBlank()) name = file.name.substringBeforeLast('.', file.name)
            inboundTag = null
            error = null
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.json_config_title)) },
                navigationIcon = { IconButton(onClick = onBack, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.cancel))
                } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.json_config_hint))
            OutlinedButton(onClick = pickFile, enabled = !busy && loaded) { Text(stringResource(Res.string.json_config_import)) }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(Res.string.json_config_name)) }, singleLine = true, enabled = !busy && loaded, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = text, onValueChange = { text = it; error = null }, label = { Text(stringResource(Res.string.json_config_content)) }, enabled = !busy && loaded, modifier = Modifier.fillMaxWidth().height(280.dp))
            Text(stringResource(Res.string.json_config_mapping_hint))
            OutlinedTextField(value = inboundTag.orEmpty(), onValueChange = { inboundTag = it.takeIf(String::isNotBlank) }, label = { Text(stringResource(Res.string.json_config_inbound)) }, singleLine = true, enabled = !busy && loaded, modifier = Modifier.fillMaxWidth())
            tags.forEach { tag -> FilterChip(selected = inboundTag == tag, onClick = { inboundTag = tag }, enabled = !busy, label = { Text(tag) }) }
            error?.let { Text(jsonConfigErrorText(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                busy = true
                component.onSaveJsonConfig(id, name, text, inboundTag) { failure ->
                    busy = false
                    error = failure
                    if (failure == null) onBack()
                }
            }, enabled = !busy && loaded && text.isNotBlank() && name.isNotBlank()) { Text(stringResource(Res.string.save)) }
            Text(stringResource(Res.string.json_config_limits), style = MaterialTheme.typography.bodySmall)
        }
    }
}
