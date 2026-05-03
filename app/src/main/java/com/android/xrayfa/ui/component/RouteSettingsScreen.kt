package com.android.xrayfa.ui.component

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.R
import com.android.xrayfa.common.repository.DomainStrategy
import com.android.xrayfa.common.repository.RoutingMode
import com.android.xrayfa.common.repository.Rule
import com.android.xrayfa.ui.navigation.RouteSettings
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSettingsScreen(
    viewmodel: SettingsViewmodel,
    sharedTransitionScope: SharedTransitionScope,
) {
    val settingsState by viewmodel.settingsState.collectAsState()
    val gson = remember { Gson() }
    val ruleType = object : TypeToken<List<Rule>>() {}.type

    // Presets from defaultRoutes
    val telegramRule = remember { Rule(type = "field", outboundTag = "proxy", domain = listOf("geosite:telegram", "geosite:google"), ruleTag = "Proxy Telegram & Google") }
    val chinaRule = remember { Rule(type = "field", outboundTag = "direct", domain = listOf("geosite:cn", "geosite:geolocation-cn"), ip = listOf("geoip:cn"), ruleTag = "Bypass Mainland China") }
    val adBlockRule = remember { Rule(type = "field", outboundTag = "block", domain = listOf("geosite:category-ads-all"), ruleTag = "Ad Block") }

    // Local state for all rules
    var allRules by remember(settingsState.routingRules) {
        mutableStateOf(gson.fromJson<List<Rule>>(settingsState.routingRules, ruleType) ?: emptyList())
    }

    // Filter out presets to show only custom rules in the manual list
    val customRules = allRules.filter { rule ->
        val isSystem = rule.inboundTag?.any { it == "api" || it == "tun" } == true
        val isPreset = rule.ruleTag == telegramRule.ruleTag ||
                       rule.ruleTag == chinaRule.ruleTag ||
                       rule.ruleTag == adBlockRule.ruleTag ||
                       // Fallback for rules without tags (old data)
                       (rule.domain?.contains("geosite:cn") == true) ||
                       (rule.domain?.contains("geosite:telegram") == true) ||
                       (rule.domain?.contains("geosite:category-ads-all") == true)

        !isSystem && !isPreset
    }

    var showAddSheet by remember { mutableStateOf(false) }
    val isRouteMode = settingsState.routingMode == RoutingMode.ROUTE

    val saveRules = { rules: List<Rule> ->
        // Priority: 1. System Rules (API/Tun), 2. Custom Rules, 3. Presets
        val system = rules.filter { it.inboundTag?.contains("api") == true || it.inboundTag?.contains("tun") == true }
        val presets = rules.filter {
            it.ruleTag == telegramRule.ruleTag ||
            it.ruleTag == chinaRule.ruleTag ||
            it.ruleTag == adBlockRule.ruleTag
        }
        val custom = rules.filter { it !in system && it !in presets }

        viewmodel.setRoutingRules(system + custom + presets)
    }

    with(sharedTransitionScope) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.route_settings_title), fontWeight = FontWeight.Bold) }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { if (isRouteMode) showAddSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                    text = { Text("Add Custom Rule") },
                    containerColor = if (isRouteMode) FloatingActionButtonDefaults.containerColor else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isRouteMode) contentColorFor(FloatingActionButtonDefaults.containerColor) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = RouteSettings.route),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    ),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Routing Mode Section ---
                item {
                    SettingsGroup(groupName = stringResource(R.string.routing_mode_label)) {
                        RoutingModeSelector(
                            currentMode = settingsState.routingMode,
                            onModeSelected = { viewmodel.setRoutingMode(it) }
                        )
                    }
                }

                // --- Domain Strategy Section ---
                item {
                    SettingsGroup(groupName = "Domain Strategy") {
                        DomainStrategySelector(
                            currentStrategy = settingsState.domainStrategy,
                            onStrategySelected = { viewmodel.setDomainStrategy(it) }
                        )
                    }
                }

                // --- Quick Config Section ---
                item {
                    SettingsGroup(groupName = "Quick Config Presets") {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            QuickConfigCheckbox(
                                label = "Bypass Mainland China",
                                description = "geosite:cn, geoip:cn -> Direct",
                                checked = allRules.any { it.ruleTag == chinaRule.ruleTag },
                                enabled = isRouteMode,
                                onCheckedChange = { checked ->
                                    val newList = allRules.toMutableList()
                                    if (checked) {
                                        // Presets are added to the list,
                                        // but saveRules will sort them to the bottom.
                                        newList.add(chinaRule)
                                    } else {
                                        newList.removeAll { it.ruleTag == chinaRule.ruleTag }
                                    }
                                    allRules = newList
                                    saveRules(newList)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            QuickConfigCheckbox(
                                label = "Proxy Telegram & Google",
                                description = "geosite:telegram, geosite:google -> Proxy",
                                checked = allRules.any { it.ruleTag == telegramRule.ruleTag },
                                enabled = isRouteMode,
                                onCheckedChange = { checked ->
                                    val newList = allRules.toMutableList()
                                    if (checked) {
                                        newList.add(telegramRule)
                                    } else {
                                        newList.removeAll { it.ruleTag == telegramRule.ruleTag }
                                    }
                                    allRules = newList
                                    saveRules(newList)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            QuickConfigCheckbox(
                                label = "Block Ads",
                                description = "geosite:category-ads-all -> Block",
                                checked = allRules.any { it.ruleTag == adBlockRule.ruleTag },
                                enabled = isRouteMode,
                                onCheckedChange = { checked ->
                                    val newList = allRules.toMutableList()
                                    if (checked) {
                                        newList.add(adBlockRule)
                                    } else {
                                        newList.removeAll { it.ruleTag == adBlockRule.ruleTag }
                                    }
                                    allRules = newList
                                    saveRules(newList)
                                }
                            )
                        }
                    }
                }

                // --- Manual Config Section ---
                item {
                    Text(
                        text = "Custom Routing Rules",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isRouteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(customRules) { _, rule ->
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        ManualRuleCard(
                            rule = rule,
                            enabled = isRouteMode,
                            onRuleChanged = { updatedRule ->
                                val newList = allRules.toMutableList()
                                val indexInAll = newList.indexOf(rule)
                                if (indexInAll != -1) {
                                    newList[indexInAll] = updatedRule
                                    allRules = newList
                                    saveRules(newList)
                                }
                            },
                            onDelete = {
                                val newList = allRules.toMutableList()
                                newList.remove(rule)
                                allRules = newList
                                saveRules(newList)
                            }
                        )
                    }
                }
            }
        }

        if (showAddSheet) {
            AddRuleBottomSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { newRule ->
                    // Custom rules are added to the list,
                    // saveRules will ensure they are placed above presets.
                    val newList = allRules + newRule
                    allRules = newList
                    saveRules(newList)
                    showAddSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (Rule) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var ruleTag by remember { mutableStateOf("") }
    var outboundTag by remember { mutableStateOf("proxy") }
    var domains by remember { mutableStateOf("") }
    var ips by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create Custom Rule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = ruleTag,
                onValueChange = { ruleTag = it },
                label = { Text("Rule Name / Tag (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) }
            )

            OutlinedTextField(
                value = outboundTag,
                onValueChange = { outboundTag = it },
                label = { Text("Outbound Tag (proxy/direct/block)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = domains,
                onValueChange = { domains = it },
                label = { Text("Domains (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = ips,
                onValueChange = { ips = it },
                label = { Text("IPs (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port (e.g., 53, 1-1024)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    val dList = domains.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val iList = ips.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(Rule(
                        type = "field",
                        outboundTag = outboundTag,
                        domain = if (dList.isEmpty()) null else dList,
                        ip = if (iList.isEmpty()) null else iList,
                        port = if (port.isEmpty()) null else port,
                        ruleTag = if (ruleTag.isBlank()) null else ruleTag
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Confirm and Add")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainStrategySelector(
    currentStrategy: Int,
    onStrategySelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val strategies = listOf(
        "AsIs" to DomainStrategy.ASIS,
        "IPIfNonMatch" to DomainStrategy.IP_IF_NON_MATCH,
        "IPOnDemand" to DomainStrategy.IP_ON_DEMAND
    )

    Box(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = strategies.find { it.second == currentStrategy }?.first ?: "Unknown",
                onValueChange = {},
                readOnly = true,
                label = { Text("Strategy") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                strategies.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onStrategySelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun QuickConfigCheckbox(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                text = label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ) 
        },
        supportingContent = { 
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            ) 
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
fun ManualRuleCard(
    rule: Rule,
    enabled: Boolean = true,
    onRuleChanged: (Rule) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.ruleTag ?: "Custom Rule",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                    )
                    if (rule.ruleTag != null) {
                        Text(
                            text = "Custom Rule",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = rule.outboundTag ?: "",
                onValueChange = { onRuleChanged(rule.copy(outboundTag = it)) },
                label = { Text("Outbound Tag") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = MaterialTheme.shapes.medium
            )
            
            val domainsStr = rule.domain?.joinToString(", ") ?: ""
            if (domainsStr.isNotEmpty()) {
                Text(
                    text = "Domains: $domainsStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val ipsStr = rule.ip?.joinToString(", ") ?: ""
            if (ipsStr.isNotEmpty()) {
                Text(
                    text = "IPs: $ipsStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!rule.port.isNullOrEmpty()) {
                Text(
                    text = "Port: ${rule.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingModeSelector(
    currentMode: Int,
    onModeSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(
        stringResource(R.string.routing_mode_global) to RoutingMode.GLOBAL,
        stringResource(R.string.routing_mode_route) to RoutingMode.ROUTE
    )

    Box(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = modes.find { it.second == currentMode }?.first ?: "Unknown",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.routing_mode_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                modes.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onModeSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}