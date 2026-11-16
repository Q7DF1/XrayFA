package com.android.xrayfa.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Attaches storage requests to a separate outbound while preserving the default outbound. */
object XDrivePreProxy {
    private const val TAG_PREFIX = "xrayfa-pre"

    fun attach(source: String, preOutbound: JsonObject): String {
        val root = JsonVpnConfig.inspect(source)
        val outbounds = root.getValue("outbounds") as JsonArray
        val tag = uniqueTag(root, outbounds)
        var attached = false
        val updatedOutbounds = outbounds.map { element ->
            val outbound = element.jsonObject
            val stream = outbound["streamSettings"] as? JsonObject
            if (stream?.get("network") != JsonPrimitive("xdrive")) {
                outbound
            } else {
                attached = true
                attachDialer(outbound, stream, tag)
            }
        }
        if (!attached) throw NodePreProxyException(NodePreProxyError.INVALID)

        val taggedPreOutbound = JsonObject(preOutbound + ("tag" to JsonPrimitive(tag)))
        return JsonObject(root + ("outbounds" to JsonArray(updatedOutbounds + taggedPreOutbound))).toString()
    }

    private fun uniqueTag(root: JsonObject, outbounds: JsonArray): String {
        val inbounds = (root["inbounds"] as? JsonArray).orEmpty()
        val tags = (outbounds + inbounds).mapNotNull { element ->
            (element.jsonObject["tag"] as? JsonPrimitive)?.content
        }.toSet()
        var tag = TAG_PREFIX
        var suffix = 0
        while (tag in tags) tag = "$TAG_PREFIX-${++suffix}"
        return tag
    }

    private fun attachDialer(outbound: JsonObject, stream: JsonObject, tag: String): JsonObject {
        val socketOptions = (stream["sockopt"] as? JsonObject).orEmpty()
        val updatedSocketOptions = JsonObject(socketOptions + ("dialerProxy" to JsonPrimitive(tag)))
        val updatedStream = JsonObject(stream + ("sockopt" to updatedSocketOptions))
        return JsonObject(outbound + ("streamSettings" to updatedStream))
    }
}
