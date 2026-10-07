package com.android.xrayfa.shared.vpn

import com.android.xrayfa.config.*
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.model.*

expect fun jsonVpnTransport(settings: SettingsState): JsonVpnTransport

fun prepareJsonVpn(node: Node, settings: SettingsState): String = JsonVpnConfig.prepare(
    node.jsonData ?: throw JsonConfigException(JsonConfigError.INVALID_CONFIG), jsonVpnTransport(settings), node.jsonInboundTag,
)
