package com.android.xrayfa.shared.config

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.repository.ConfigParserSettings
import com.android.xrayfa.common.repository.ConfigParserSettingsProvider
import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.common.utils.UrlCodec
import com.android.xrayfa.config.KotlinxXrayConfigEncoder
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.*
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.platform.ClipboardReader
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.VpnState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class NodeFormTransportTest {
    private val settings = object : ConfigParserSettingsProvider {
        override suspend fun getConfigParserSettings() = ConfigParserSettings(
            socksListen = "127.0.0.1", socksPort = 10808, socksUserName = "test", socksPassword = "test",
            lanHttpProxyEnable = false, httpPort = 10809, dnsIPv4 = "1.1.1.1", dnsIPv6 = "",
            ipV6Enable = false, domainStrategy = 0, routingRules = "", routingMode = 0, geoLiteInstall = false,
        )
    }
    private val geo = object : GeoIpProvider { override fun countryIsoFromIp(ip: String) = "" }
    private val encoder = KotlinxXrayConfigEncoder()
    private val factory = ParserFactory(
        VLESSConfigParser(settings, geo, encoder), VMESSConfigParser(settings, geo, encoder),
        TrojanConfigParser(settings, geo, encoder), ShadowSocksConfigParser(settings, geo, encoder),
        Hysteria2ConfigParser(settings, geo, encoder), SocksConfigParser(settings, geo, encoder),
        HttpConfigParser(settings, geo, encoder),
    )
    private val nodes = FormTestNodes()
    private val logger = object : Logger {
        override fun i(tag: String, message: String) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
    private val vpn = object : VpnController {
        override val state = MutableStateFlow<VpnState>(VpnState.Disconnected)
        override val connectError = MutableStateFlow<String?>(null)
        override suspend fun connect() = false
        override fun disconnect() = Unit
        override suspend fun restartIfNeeded() = Unit
    }
    private val editor = NodeFormEditor(factory, nodes, vpn, logger)
    private val id = "00000000-0000-0000-0000-000000000001"

    @Test
    fun editingRemarkRetainsXhttpAndCertificateParameters() {
        val extra = """{"xmux":{"maxConnections":3},"noSSEHeader":true}"""
        val link = "vless://$id@example.com:443?type=xhttp&security=tls&mode=packet-up&path=%2Fup&host=cdn.example&sni=cert.example&alpn=h2,http%2F1.1&pcs=abcd&vcn=cert.example&extra=${UrlCodec.encode(extra)}"
        val form = editor.parseForm("vless", link).copy(remarks = "renamed")
        val edited = factory.vlessConfigParser.decodeProtocol(editor.encodeUrl(form))
        for (key in listOf("extra", "pcs", "vcn", "alpn", "mode", "path", "host")) {
            assertEquals(factory.vlessConfigParser.decodeProtocol(link).param[key], edited.param[key], key)
        }
        assertEquals("renamed", edited.remark)
        factory.vlessConfigParser.parseOutbound(editor.encodeUrl(form))
    }

    @Test
    fun editingRealityRetainsVerificationAndSpiderParameters() {
        val link = "trojan://password@example.com?security=reality&pbk=key&sid=ab&spx=%2Fprobe&pqv=verify"
        val form = editor.parseForm("trojan", link).copy(remarks = "changed")
        val edited = factory.trojanConfigParser.decodeProtocol(editor.encodeUrl(form))
        assertEquals("key", edited.params["pbk"])
        assertEquals("/probe", edited.params["spx"])
        assertEquals("verify", edited.params["pqv"])
        assertEquals("chrome", edited.params["fp"])
    }

    @Test
    fun editingVmessRetainsHttpCamouflageAndAdditionalFields() {
        val payload = """{"add":"example.com","port":443,"id":"$id","net":"tcp","type":"http","host":"cdn.example","path":"/one,/two","tls":"tls","sni":"cert.example","alpn":"h2","pcs":"abcd"}"""
        val link = "vmess://" + Base64Compat.encode(payload.encodeToByteArray())
        val form = editor.parseForm("vmess", link).copy(remarks = "changed")
        val json = factory.vmessConfigParser.decodeProtocol(editor.encodeUrl(form)).others
        for (key in listOf("type", "host", "path", "sni", "alpn", "pcs")) {
            assertEquals(Json.parseToJsonElement(payload).jsonObject[key], json[key], key)
        }
    }

    @Test
    fun switchingProtocolsDoesNotCarryImportedExtras() {
        val link = "vless://$id@example.com:443?extra=%7B%7D&pcs=abcd&path=%2Fimported"
        val form = editor.parseForm("vless", link).copy(selectedProtocol = Protocol.TROJAN, uuidOrPassword = "password")
        val params = factory.trojanConfigParser.decodeProtocol(editor.encodeUrl(form)).params
        assertNull(params["extra"])
        assertNull(params["pcs"])
        assertNull(params["path"])
    }

    @Test
    fun clipboardKeepsCommaSeparatedAlpnAndAcceptsHy2() = runBlocking {
        val clipboard = object : ClipboardReader {
            override fun readText() = "vless://$id@example.com:443?security=tls&alpn=h2,http%2F1.1,hy2://auth@example.com?obfs=salamander&obfs-password=secret"
        }
        ConfigLinkImporter(nodes, factory, clipboard, logger).importFromClipboard()
        assertEquals(2, nodes.nodes.value.size)
        assertEquals("h2,http/1.1", factory.vlessConfigParser.decodeProtocol(nodes.nodes.value.first().url).param["alpn"])
        assertEquals("hysteria2", nodes.nodes.value.last().protocolPrefix)
    }

    @Test
    fun invalidSettingsAreNotSaved() = runBlocking {
        val invalid = NodeEditForm(address = "example.com", port = "443", uuidOrPassword = id, network = "quic")
        assertFalse(editor.saveForm(0, invalid))
        assertTrue(nodes.nodes.value.isEmpty())
    }
}

private class FormTestNodes : NodeRepository {
    val nodes = MutableStateFlow<List<Node>>(emptyList())
    override val allNodes = nodes
    override val favorites = nodes.map { it.filter(Node::favorite) }
    override suspend fun addNode(vararg nodes: Node) { this.nodes.value += nodes }
    override suspend fun deleteLink(link: Node) = error("Unexpected mutation")
    override fun loadLinksById(id: Int) = flowOf<Node?>(null)
    override suspend fun clearSelection() = error("Unexpected mutation")
    override fun querySelectedNode() = flowOf<Node?>(null)
    override fun queryPreNode() = flowOf<Node?>(null)
    override fun queryNextNode() = flowOf<Node?>(null)
    override suspend fun updateNode(id: Int, url: String, port: Int, remark: String?) = error("Unexpected mutation")
    override suspend fun updateSelectById(id: Int, selected: Boolean) = error("Unexpected mutation")
    override suspend fun updateFavoriteById(id: Int, favorite: Boolean) = error("Unexpected mutation")
    override suspend fun deleteLinkById(id: Int) = error("Unexpected mutation")
    override suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) = error("Unexpected mutation")
    override suspend fun deleteAllNodes() = error("Unexpected mutation")
}
