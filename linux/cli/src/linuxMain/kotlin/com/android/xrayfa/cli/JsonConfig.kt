package com.android.xrayfa.cli

import com.android.xrayfa.config.JsonVpnConfig
import com.android.xrayfa.config.XDriveForm
import kotlinx.serialization.json.*

/** Import outbound configurations without permitting additional listeners or file logging. */
internal fun inspectCliJson(text: String): JsonObject {
    val root = JsonVpnConfig.inspect(text)
    require(root.keys.all { it in setOf("outbounds", "routing", "dns", "policy", "inbounds", "log") })
    val rules = root["routing"]?.jsonObject?.get("rules")?.jsonArray.orEmpty()
    require(rules.none { "inboundTag" in it.jsonObject }) { "Inbound-specific routing is unsupported" }
    val xdrive = root.getValue("outbounds").jsonArray.any {
        it.jsonObject["streamSettings"]?.jsonObject?.get("network") == JsonPrimitive("xdrive")
    }
    if (xdrive) {
        val form = XDriveForm.fromJson(text)
        require(form != null && form.issue() == null) { "Complete XDRIVE storage settings are required" }
    }
    return root
}

internal fun nodeSocksConfig(record: JsonObject, outbound: JsonObject, level: LogLevel): JsonObject {
    if (record["protocol"] != JsonPrimitive("json-config")) return socksConfig(outbound, level)
    val source = inspectCliJson(record.getValue("url").jsonPrimitive.content)
    val transport = socksConfig(outbound, level)
    return JsonObject(source + mapOf("inbounds" to transport.getValue("inbounds"), "log" to transport.getValue("log")))
}
