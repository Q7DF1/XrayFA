package com.android.xrayfa.model

import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.UrlCodec
import com.android.xrayfa.config.GsonXrayConfigEncoder
import com.android.xrayfa.config.KotlinxXrayConfigEncoder
import com.android.xrayfa.parser.ParserTestFixtures
import com.google.gson.Gson
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.*

/** Export generated JSON for source-pinned Xray loader validation without connecting to servers. */
class CoreConfigFixturesTest {
    @Test
    fun exportRuntimeConfigurationsForCoreValidation() {
        val id = "00000000-0000-0000-0000-000000000001"
        val key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val extra = """{"xmux":{"maxConnections":3},"noSSEHeader":true,"downloadSettings":{"address":"example.com","port":443,"network":"xhttp","security":"tls","tlsSettings":{"serverName":"example.com"},"xhttpSettings":{"path":"/download"}}}"""
        val links = linkedMapOf(
            "vless-raw" to "vless://$id@192.0.2.1:443",
            "vless-default-routing" to "vless://$id@192.0.2.1:443",
            "vless-ws" to "vless://$id@192.0.2.1:443?type=ws&security=tls&sni=cert.example&host=cdn.example&alpn=http%2F1.1",
            "vless-xhttp" to "vless://$id@example.com:443?type=xhttp&security=tls&extra=${UrlCodec.encode(extra)}",
            "vless-reality" to "vless://$id@example.com:443?security=reality&pbk=$key&sid=00&fp=chrome",
            "vless-grpc" to "vless://$id@example.com:443?type=grpc&security=tls&serviceName=service&mode=multi&authority=cdn.example",
            "vless-upgrade" to "vless://$id@example.com:443?type=httpupgrade&security=tls&path=%2Fup&host=cdn.example",
            "vless-cert-pin" to "vless://$id@example.com:443?security=tls&pcs=${"a".repeat(64)}&vcn=cert.example",
            "trojan-xhttp" to "trojan://password@example.com?type=xhttp&security=reality&pbk=$key",
            "hysteria-obfs" to "hy2://auth@example.com?obfs=salamander&obfs-password=password&sni=cert.example&alpn=h3",
            "vless-kcp-current" to "vless://$id@example.com:443?type=kcp&security=tls&mtu=1350&tti=50&cwndMultiplier=2&maxSendingWindow=2097152",
            "socks-ipv6" to "socks://user:$id@[2001:db8::1]:1080",
            "http-ipv6" to "http://user:$id@[2001:db8::1]:8080",
            "ss-ipv6" to "ss://${Base64Compat.encode("aes-256-gcm:$id".encodeToByteArray())}@[2001:db8::1]:8388",
            "vless-ipv6" to "vless://$id@[2001:db8::1]:443?security=tls&sni=cert.example",
        )
        for (header in listOf("none", "http")) {
            val json = """{"add":"example.com","port":443,"id":"$id","net":"tcp","type":"$header","host":"cdn.example","path":"/one,/two"}"""
            links["vmess-$header"] = "vmess://" + Base64Compat.encode(json.encodeToByteArray())
        }
        val vmessReality = """{"add":"example.com","port":443,"id":"$id","net":"raw","tls":"reality","pbk":"$key","sid":"abcd","fp":"chrome"}"""
        links["vmess-reality"] = "vmess://" + Base64Compat.encode(vmessReality.encodeToByteArray())
        val directory = File("build/generated/xray-config-fixtures").apply { mkdirs() }
        val factory = ParserTestFixtures.factory()
        for ((name, link) in links) {
            val config = XrayConfiguration(
                inbounds = emptyList(), outbounds = listOf(factory.getParser(link).parseOutbound(link)),
                routing = if (name == "vless-default-routing") RoutingObject(rules = listOf(
                    RuleObject(outboundTag = "proxy", domain = listOf("geosite:telegram", "geosite:google",
                        "geosite:cn", "geosite:geolocation-cn", "geosite:private", "geosite:category-ads-all")),
                    RuleObject(outboundTag = "proxy", ip = listOf("geoip:private", "geoip:cn")),
                )) else null,
            )
            for ((encoderName, encoder) in listOf("gson" to GsonXrayConfigEncoder(Gson()), "kotlinx" to KotlinxXrayConfigEncoder())) {
                val text = encoder.encode(config)
                File(directory, "$name-$encoderName.json").writeText(text)
                val root = Json.parseToJsonElement(text).jsonObject
                assertEquals(1, root["outbounds"]!!.jsonArray.size)
            }
        }
    }
}
