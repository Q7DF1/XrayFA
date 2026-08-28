package com.android.xrayfa.model.serialization

import com.android.xrayfa.config.XrayJson
import com.android.xrayfa.model.AbsOutboundConfigurationObject
import com.android.xrayfa.model.HttpOutboundConfigurationObject
import com.android.xrayfa.model.Hysteria2OutboundConfigurationObject
import com.android.xrayfa.model.InboundObject
import com.android.xrayfa.model.NoneOutboundConfigurationObject
import com.android.xrayfa.model.OutboundObject
import com.android.xrayfa.model.ShadowSocksOutboundConfigurationObject
import com.android.xrayfa.model.SocksOutboundConfigurationObject
import com.android.xrayfa.model.TrojanOutboundConfigurationObject
import com.android.xrayfa.model.VLESSOutboundConfigurationObject
import com.android.xrayfa.model.VMESSOutboundConfigurationObject
import com.android.xrayfa.model.WireGuardOutboundConfigurationObject
import com.android.xrayfa.model.HttpInboundConfigurationObject
import com.android.xrayfa.model.SocksInboundConfigurationObject
import com.android.xrayfa.model.TunnelInboundConfigurationObject
import com.android.xrayfa.model.TunInboundConfigurationObject
import com.android.xrayfa.model.VLESSInboundConfigurationObject
import com.android.xrayfa.model.WireGuardInboundConfigurationObject
import com.android.xrayfa.model.AbsInboundConfigurationObject
import com.android.xrayfa.model.DnsObject
import com.android.xrayfa.model.DnsServerObject
import com.android.xrayfa.model.stream.HeaderObject
import com.android.xrayfa.model.stream.HttpHeaderObject
import com.android.xrayfa.model.stream.NoneHeaderObject
import com.android.xrayfa.model.stream.RawSettings
import com.android.xrayfa.model.stream.StreamSettingsObject
import com.android.xrayfa.model.Sockopt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

object OutboundObjectSerializer : KSerializer<OutboundObject<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("OutboundObject")

    override fun deserialize(decoder: Decoder): OutboundObject<*> {
        error("OutboundObject deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: OutboundObject<*>) {
        val jsonEncoder = encoder as JsonEncoder
        val json = buildJsonObject {
            put("sendThrough", JsonPrimitive(value.sendThrough))
            value.protocol?.let { put("protocol", JsonPrimitive(it)) }
            encodeSettings(value.settings)?.let { put("settings", it) }
            put("tag", JsonPrimitive(value.tag))
            value.streamSettings?.let { put("streamSettings", XrayJson.encodeToJsonElement(StreamSettingsObjectSerializer, it)) }
            value.proxySettings?.let { put("proxySettings", XrayJson.encodeToJsonElement(it)) }
            value.mux?.let { put("mux", XrayJson.encodeToJsonElement(it)) }
        }
        jsonEncoder.encodeJsonElement(json)
    }

    private fun encodeSettings(settings: AbsOutboundConfigurationObject?): JsonElement? = when (settings) {
        null -> null
        is NoneOutboundConfigurationObject -> JsonObject(emptyMap())
        is VLESSOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is VMESSOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is TrojanOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is ShadowSocksOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is WireGuardOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is Hysteria2OutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is SocksOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is HttpOutboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        else -> error("Unsupported outbound settings: ${settings::class.simpleName}")
    }
}

object InboundObjectSerializer : KSerializer<InboundObject> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InboundObject")

    override fun deserialize(decoder: Decoder): InboundObject {
        error("InboundObject deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: InboundObject) {
        val jsonEncoder = encoder as JsonEncoder
        val json = buildJsonObject {
            value.listen?.let { put("listen", JsonPrimitive(it)) }
            value.port?.let { put("port", JsonPrimitive(it)) }
            put("protocol", JsonPrimitive(value.protocol))
            encodeInboundSettings(value.settings)?.let { put("settings", it) }
            value.streamSettings?.let { put("streamSettings", XrayJson.encodeToJsonElement(StreamSettingsObjectSerializer, it)) }
            value.tag?.let { put("tag", JsonPrimitive(it)) }
            value.sniffing?.let { put("sniffing", XrayJson.encodeToJsonElement(it)) }
            value.allocate?.let { put("allocate", XrayJson.encodeToJsonElement(it)) }
        }
        jsonEncoder.encodeJsonElement(json)
    }

    private fun encodeInboundSettings(settings: AbsInboundConfigurationObject?): JsonElement? = when (settings) {
        null -> null
        is SocksInboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is HttpInboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is TunnelInboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is TunInboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is VLESSInboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        is WireGuardInboundConfigurationObject -> XrayJson.encodeToJsonElement(settings)
        else -> error("Unsupported inbound settings: ${settings::class.simpleName}")
    }
}

object DnsObjectSerializer : KSerializer<DnsObject> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DnsObject")

    override fun deserialize(decoder: Decoder): DnsObject {
        error("DnsObject deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: DnsObject) {
        val jsonEncoder = encoder as JsonEncoder
        val json = buildJsonObject {
            value.hosts?.let { hosts ->
                put(
                    "hosts",
                    JsonObject(
                        hosts.mapValues { (_, hostValue) ->
                            when (hostValue) {
                                is String -> JsonPrimitive(hostValue)
                                is List<*> -> JsonArray(hostValue.map { JsonPrimitive(it.toString()) })
                                else -> JsonPrimitive(hostValue.toString())
                            }
                        },
                    ),
                )
            }
            value.servers?.let { servers ->
                put(
                    "servers",
                    JsonArray(
                        servers.map { server ->
                            when (server) {
                                is String -> JsonPrimitive(server)
                                is DnsServerObject -> XrayJson.encodeToJsonElement(server)
                                else -> JsonPrimitive(server.toString())
                            }
                        },
                    ),
                )
            }
            value.clientIp?.let { put("clientIp", JsonPrimitive(it)) }
            value.queryStrategy?.let { put("queryStrategy", JsonPrimitive(it)) }
            value.disableCache?.let { put("disableCache", JsonPrimitive(it)) }
            value.disableFallback?.let { put("disableFallback", JsonPrimitive(it)) }
            value.disableFallbackIfMatch?.let { put("disableFallbackIfMatch", JsonPrimitive(it)) }
            value.useSystemHosts?.let { put("useSystemHosts", JsonPrimitive(it)) }
            putNonDefault("tag", value.tag, "dns_inbound", alwaysIncludeDefault = true)
        }
        jsonEncoder.encodeJsonElement(json)
    }
}

object StreamSettingsObjectSerializer : KSerializer<StreamSettingsObject> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StreamSettingsObject")

    override fun deserialize(decoder: Decoder): StreamSettingsObject {
        error("StreamSettingsObject deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: StreamSettingsObject) {
        val jsonEncoder = encoder as JsonEncoder
        val json = buildJsonObject {
            put("network", JsonPrimitive(value.network))
            put("security", JsonPrimitive(value.security))
            value.tlsSettings?.let { put("tlsSettings", XrayJson.encodeToJsonElement(it)) }
            value.realitySettings?.let { put("realitySettings", XrayJson.encodeToJsonElement(it)) }
            value.rawSettings?.let { put("rawSettings", XrayJson.encodeToJsonElement(RawSettingsSerializer, it)) }
            value.xhttpSettings?.let { put("xhttpSettings", XrayJson.encodeToJsonElement(it)) }
            value.kcpSettings?.let { put("kcpSettings", XrayJson.encodeToJsonElement(it)) }
            value.grpcSettings?.let { put("grpcSettings", XrayJson.encodeToJsonElement(it)) }
            value.wsSettings?.let { put("wsSettings", XrayJson.encodeToJsonElement(it)) }
            value.httpUpgradeSettings?.let { put("httpUpgradeSettings", XrayJson.encodeToJsonElement(it)) }
            value.hysteriaSettings?.let { put("hysteriaSettings", XrayJson.encodeToJsonElement(it)) }
            value.finalMask?.let { put("finalMask", XrayJson.encodeToJsonElement(it)) }
            value.sockopt?.let { put("sockopt", XrayJson.encodeToJsonElement(SockoptSerializer, it)) }
            value.quicSettings?.let { put("quicSettings", it.toJsonElement()) }
            value.dsSettings?.let { put("dsSettings", it.toJsonElement()) }
        }
        jsonEncoder.encodeJsonElement(json)
    }
}

object RawSettingsSerializer : KSerializer<RawSettings> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RawSettings")

    override fun deserialize(decoder: Decoder): RawSettings {
        error("RawSettings deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: RawSettings) {
        val jsonEncoder = encoder as JsonEncoder
        val json = buildJsonObject {
            value.acceptProxyProtocol?.let { put("acceptProxyProtocol", JsonPrimitive(it)) }
            put("header", XrayJson.encodeToJsonElement(HeaderObjectSerializer, value.header))
        }
        jsonEncoder.encodeJsonElement(json)
    }
}

object HeaderObjectSerializer : KSerializer<HeaderObject> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HeaderObject")

    override fun deserialize(decoder: Decoder): HeaderObject {
        error("HeaderObject deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: HeaderObject) {
        val jsonEncoder = encoder as JsonEncoder
        val json = when (value) {
            is NoneHeaderObject -> XrayJson.encodeToJsonElement(value)
            is HttpHeaderObject -> XrayJson.encodeToJsonElement(value)
            else -> error("Unsupported header object: ${value::class.simpleName}")
        }
        jsonEncoder.encodeJsonElement(json)
    }
}

object SockoptSerializer : KSerializer<Sockopt> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Sockopt")

    override fun deserialize(decoder: Decoder): Sockopt {
        error("Sockopt deserialization is not supported")
    }

    override fun serialize(encoder: Encoder, value: Sockopt) {
        val jsonEncoder = encoder as JsonEncoder
        val json = buildJsonObject {
            putNonDefault("mark", value.mark, 0)
            value.tcpMaxSeg?.let { put("tcpMaxSeg", JsonPrimitive(it)) }
            value.tcpFastOpen?.let { put("tcpFastOpen", it.toJsonElement()) }
            putNonDefault("tproxy", value.tproxy, "off")
            putNonDefault("domainStrategy", value.domainStrategy, "AsIs")
            value.happyEyeballs?.let { put("happyEyeballs", XrayJson.encodeToJsonElement(it)) }
            putNonDefault("dialerProxy", value.dialerProxy, "")
            putNonDefault("acceptProxyProtocol", value.acceptProxyProtocol, false)
            putNonDefault("tcpKeepAliveInterval", value.tcpKeepAliveInterval, 0)
            putNonDefault("tcpKeepAliveIdle", value.tcpKeepAliveIdle, 300)
            putNonDefault("tcpUserTimeout", value.tcpUserTimeout, 10000)
            putNonDefault("tcpCongestion", value.tcpCongestion, "bbr")
            putNonDefault("interfaceName", value.interfaceName, "")
            putNonDefault("v6only", value.v6only, false)
            putNonDefault("tcpWindowClamp", value.tcpWindowClamp, 600)
            putNonDefault("tcpMptcp", value.tcpMptcp, false)
            putNonDefault("addressPortStrategy", value.addressPortStrategy, "")
        }
        jsonEncoder.encodeJsonElement(json)
    }
}

private fun JsonObjectBuilder.putNonDefault(
    key: String,
    value: String,
    default: String,
    alwaysIncludeDefault: Boolean = false,
) {
    if (alwaysIncludeDefault || value != default) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putNonDefault(key: String, value: Int, default: Int) {
    if (value != default) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putNonDefault(key: String, value: Boolean, default: Boolean) {
    if (value != default) put(key, JsonPrimitive(value))
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    null -> JsonNull
    is Map<*, *> -> JsonObject(
        this.entries.mapNotNull { (key, mapValue) ->
            val jsonKey = key as? String ?: return@mapNotNull null
            jsonKey to mapValue.toJsonElement()
        }.toMap(),
    )
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}
