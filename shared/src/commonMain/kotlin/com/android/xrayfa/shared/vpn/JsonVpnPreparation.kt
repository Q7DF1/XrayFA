package com.android.xrayfa.shared.vpn

import com.android.xrayfa.config.JsonConfigError
import com.android.xrayfa.config.JsonConfigException
import com.android.xrayfa.config.JsonVpnConfig
import com.android.xrayfa.config.JsonVpnTransport
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.jsonInboundTag
import com.android.xrayfa.model.requireXDriveReady

expect fun jsonVpnTransport(settings: SettingsState): JsonVpnTransport

fun prepareJsonVpn(node: Node, settings: SettingsState, source: String? = node.jsonData): String {
    node.requireXDriveReady()
    return JsonVpnConfig.prepare(
        text = source ?: throw JsonConfigException(JsonConfigError.INVALID_CONFIG),
        transport = jsonVpnTransport(settings),
        inboundTag = node.jsonInboundTag,
    )
}
