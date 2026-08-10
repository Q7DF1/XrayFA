package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.datastore.DomainStrategy
import com.android.xrayfa.datastore.RoutingMode
import com.android.xrayfa.datastore.Rule
import com.android.xrayfa.datastore.decodeRules
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.widgets.SharedModalBottomSheet
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private object RoutePresetTags {
    const val TELEGRAM = "Proxy Telegram & Google"
    const val CHINA = "Bypass Mainland China"
    const val AD_BLOCK = "Ad Block"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedRouteSettingsScreen(
    component: SettingsComponent,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    labels: RouteSettingsUiLabels = RouteSettingsUiLabels(),
) {
    val settingsState by component.state.subscribeAsState()

    val telegramRule =
        remember {
            Rule(
                type = "field",
                outboundTag = "proxy",
                domain = listOf("geosite:telegram", "geosite:google"),
                ruleTag = RoutePresetTags.TELEGRAM,
            )
        }
    val chinaRule =
        remember {
            Rule(
                type = "field",
                outboundTag = "direct",
                domain = listOf("geosite:cn", "geosite:geolocation-cn"),
                ip = listOf("geoip:cn"),
                ruleTag = RoutePresetTags.CHINA,
            )
        }
    val adBlockRule =
        remember {
            Rule(
                type = "field",
                outboundTag = "block",
                domain = listOf("geosite:category-ads-all"),
                ruleTag = RoutePresetTags.AD_BLOCK,
            )
        }

    var allRules by remember(settingsState.routingRules) {
        mutableStateOf(decodeRules(settingsState.routingRules))
    }

    val customRules =
        allRules.filter { rule ->
            val isSystem = rule.inboundTag?.any { it == "api" || it == "tun" } == true
            val isPreset =
                rule.ruleTag == telegramRule.ruleTag ||
                    rule.ruleTag == chinaRule.ruleTag ||
                    rule.ruleTag == adBlockRule.ruleTag ||
                    rule.domain?.contains("geosite:cn") == true ||
                    rule.domain?.contains("geosite:telegram") == true ||
                    rule.domain?.contains("geosite:category-ads-all") == true
            !isSystem && !isPreset
        }

    var showAddSheet by remember { mutableStateOf(false) }
    val isRouteMode = settingsState.routingMode == RoutingMode.ROUTE.code

    val saveRules = { rules: List<Rule> ->
        val system =
            rules.filter {
                it.inboundTag?.contains("api") == true || it.inboundTag?.contains("tun") == true
            }
        val presets =
            rules.filter {
                it.ruleTag == telegramRule.ruleTag ||
                    it.ruleTag == chinaRule.ruleTag ||
                    it.ruleTag == adBlockRule.ruleTag
            }
        val custom = rules.filter { it !in system && it !in presets }
        component.onSetRoutingRules(system + custom + presets)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(labels.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = labels.backLabel,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (isRouteMode) showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = labels.addCustomRuleLabel) },
                text = { Text(labels.addCustomRuleLabel) },
                containerColor =
                    if (isRouteMode) {
                        FloatingActionButtonDefaults.containerColor
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                contentColor =
                    if (isRouteMode) {
                        contentColorFor(FloatingActionButtonDefaults.containerColor)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SharedSettingsGroup(groupName = labels.routingModeSectionTitle) {
                    SharedRoutingModeSelector(
                        labels = labels,
                        currentMode = RoutingMode.fromCode(settingsState.routingMode),
                        onModeSelected = component::onSetRoutingMode,
                    )
                }
            }

            item {
                SharedSettingsGroup(groupName = labels.domainStrategySectionTitle) {
                    SharedDomainStrategySelector(
                        labels = labels,
                        currentStrategy = DomainStrategy.fromCode(settingsState.domainStrategy),
                        onStrategySelected = component::onSetDomainStrategy,
                    )
                }
            }

            item {
                SharedSettingsGroup(groupName = labels.quickConfigSectionTitle) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SharedRoutePresetCheckbox(
                            label = labels.bypassChinaLabel,
                            description = labels.bypassChinaDescription,
                            checked = allRules.any { it.ruleTag == chinaRule.ruleTag },
                            enabled = isRouteMode,
                            onCheckedChange = { checked ->
                                val newList = allRules.toMutableList()
                                if (checked) {
                                    newList.add(chinaRule)
                                } else {
                                    newList.removeAll { it.ruleTag == chinaRule.ruleTag }
                                }
                                allRules = newList
                                saveRules(newList)
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SharedRoutePresetCheckbox(
                            label = labels.proxyTelegramGoogleLabel,
                            description = labels.proxyTelegramGoogleDescription,
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
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SharedRoutePresetCheckbox(
                            label = labels.blockAdsLabel,
                            description = labels.blockAdsDescription,
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
                            },
                        )
                    }
                }
            }

            item {
                Text(
                    text = labels.customRulesSectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (isRouteMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            itemsIndexed(customRules) { _, rule ->
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SharedManualRuleCard(
                        rule = rule,
                        labels = labels,
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
                        },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        SharedAddRuleBottomSheet(
            labels = labels,
            onDismiss = { showAddSheet = false },
            onConfirm = { newRule ->
                val newList = allRules + newRule
                allRules = newList
                saveRules(newList)
                showAddSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedAddRuleBottomSheet(
    labels: RouteSettingsUiLabels,
    onDismiss: () -> Unit,
    onConfirm: (Rule) -> Unit,
) {
    var ruleTag by remember { mutableStateOf("") }
    var outboundTag by remember { mutableStateOf("proxy") }
    var domains by remember { mutableStateOf("") }
    var ips by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    SharedModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = labels.createCustomRuleTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = ruleTag,
                onValueChange = { ruleTag = it },
                label = { Text(labels.ruleNameLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
            )

            OutlinedTextField(
                value = outboundTag,
                onValueChange = { outboundTag = it },
                label = { Text(labels.outboundTagLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = domains,
                onValueChange = { domains = it },
                label = { Text(labels.domainsLabel) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = ips,
                onValueChange = { ips = it },
                label = { Text(labels.ipsLabel) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text(labels.portLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    val domainList = domains.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val ipList = ips.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(
                        Rule(
                            type = "field",
                            outboundTag = outboundTag,
                            domain = domainList.ifEmpty { null },
                            ip = ipList.ifEmpty { null },
                            port = port.ifEmpty { null },
                            ruleTag = ruleTag.ifBlank { null },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(labels.confirmAddLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedDomainStrategySelector(
    labels: RouteSettingsUiLabels,
    currentStrategy: DomainStrategy,
    onStrategySelected: (DomainStrategy) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strategies =
        listOf(
            labels.domainStrategyAsIsLabel to DomainStrategy.ASIS,
            labels.domainStrategyIpIfNonMatchLabel to DomainStrategy.IP_IF_NON_MATCH,
            labels.domainStrategyIpOnDemandLabel to DomainStrategy.IP_ON_DEMAND,
        )

    Box(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = strategies.find { it.second == currentStrategy }?.first ?: labels.unknownLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(labels.strategyFieldLabel) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                strategies.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onStrategySelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedRoutePresetCheckbox(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SharedManualRuleCard(
    rule: Rule,
    labels: RouteSettingsUiLabels,
    enabled: Boolean = true,
    onRuleChanged: (Rule) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (enabled) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.ruleTag ?: labels.customRuleFallbackTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                            if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                            },
                    )
                    if (rule.ruleTag != null) {
                        Text(
                            text = labels.customRuleFallbackTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (enabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                        )
                    }
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = labels.deleteLabel,
                        tint =
                            if (enabled) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                            },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = rule.outboundTag.orEmpty(),
                onValueChange = { onRuleChanged(rule.copy(outboundTag = it)) },
                label = { Text(labels.outboundTagShortLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
            )

            val domainsStr = rule.domain?.joinToString(", ").orEmpty()
            if (domainsStr.isNotEmpty()) {
                Text(
                    text = "${labels.domainsPrefix} $domainsStr",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            val ipsStr = rule.ip?.joinToString(", ").orEmpty()
            if (ipsStr.isNotEmpty()) {
                Text(
                    text = "${labels.ipsPrefix} $ipsStr",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (!rule.port.isNullOrEmpty()) {
                Text(
                    text = "${labels.portPrefix} ${rule.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedRoutingModeSelector(
    labels: RouteSettingsUiLabels,
    currentMode: RoutingMode,
    onModeSelected: (RoutingMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modes =
        listOf(
            labels.routingModeGlobalLabel to RoutingMode.GLOBAL,
            labels.routingModeRouteLabel to RoutingMode.ROUTE,
        )

    Box(modifier = Modifier.padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = modes.find { it.second == currentMode }?.first ?: labels.unknownLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(labels.routingModeSectionTitle) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                modes.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onModeSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
