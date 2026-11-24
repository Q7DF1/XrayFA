package com.android.xrayfa.cli

import com.android.xrayfa.config.JsonVpnConfig
import com.android.xrayfa.config.XDriveForm
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Import outbound configurations without permitting additional listeners or file logging. */
internal fun inspectCliJson(text: String): JsonObject {
    val root = JsonVpnConfig.inspect(text)
    if (!root.keys.all { it in setOf("outbounds", "routing", "dns", "policy", "inbounds", "log") })
        fail(ExitCode.CONFIGURATION, "JSON contains unsupported top-level services")
    val rules = root["routing"]?.jsonObject?.get("rules")?.jsonArray.orEmpty()
    if (rules.any { "inboundTag" in it.jsonObject } && root["inbounds"]?.jsonArray.orEmpty().size > 1)
        fail(ExitCode.CONFIGURATION,
            "JSON routing uses inboundTag with multiple inbounds; CLI provides one local SOCKS5 inbound and cannot choose the original inbound")
    val xdrive = root.getValue("outbounds").jsonArray.any {
        it.jsonObject["streamSettings"]?.jsonObject?.get("network") == JsonPrimitive("xdrive")
    }
    if (xdrive) {
        val form = XDriveForm.fromJson(text)
        if (form == null || form.issue() != null)
            fail(ExitCode.CONFIGURATION, "Complete XDRIVE storage settings are required")
    }
    return root
}

internal fun nodeSocksConfig(record: JsonObject, outbound: JsonObject, level: LogLevel): JsonObject {
    if (record["protocol"] != JsonPrimitive("json-config")) return socksConfig(outbound, level)
    val source = inspectCliJson(record.getValue("url").jsonPrimitive.content)
    val transport = socksConfig(outbound, level)
    val originalTag = source["inbounds"]?.jsonArray?.singleOrNull()?.jsonObject
        ?.get("tag")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    val referencedTags = source["routing"]?.jsonObject?.get("rules")?.jsonArray.orEmpty()
        .flatMap { it.jsonObject["inboundTag"]?.jsonArray.orEmpty() }
        .map { it.jsonPrimitive.content }.toSet()
    // Preserve the sole original inbound's identity. An untagged/replaced set of
    // inbounds must not accidentally activate a previously unmatched rule.
    val tag = originalTag ?: generateSequence("socks") { "$it-1" }.first { it !in referencedTags }
    val inbound = JsonObject(transport.getValue("inbounds").jsonArray.single().jsonObject +
        ("tag" to JsonPrimitive(tag)))
    return JsonObject(source + mapOf("inbounds" to JsonArray(listOf(inbound)), "log" to transport.getValue("log")))
}

/** TUN owns DNS/routing; reject imported policies instead of silently discarding them. */
internal fun nodeTunOutbound(record: JsonObject, outbound: JsonObject): JsonObject {
    if (record["protocol"] != JsonPrimitive("json-config")) return outbound
    val source = inspectCliJson(record.getValue("url").jsonPrimitive.content)
    if (source.getValue("outbounds").jsonArray.size != 1 ||
        source.keys.any { it in setOf("routing", "dns", "policy") })
        fail(ExitCode.CONFIGURATION,
            "TUN JSON requires one outbound with managed DNS/routing; imported chains, routing, DNS and policy are unsupported")
    return outbound
}
