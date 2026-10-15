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
    private fun validForm(protocol: Protocol = Protocol.VLESS) = NodeEditForm(
        selectedProtocol = protocol, address = "example.com", port = "443", uuidOrPassword = id,
    )

    @Test
    fun newProtocolDefaultsAndSwitchesDoNotLeakCredentialsOrExtras() {
        assertEquals(Protocol.TROJAN, editor.parseForm("trojan", null).selectedProtocol)
        assertEquals("tls", editor.parseForm("trojan", null).transportSecurity)
        val switched = validForm().copy(advancedParams = mapOf("extra" to "{}"), flow = "xtls-rprx-vision")
            .selectProtocol(Protocol.TROJAN)
        assertEquals("example.com", switched.address)
        assertEquals("443", switched.port)
        assertEquals("tls", switched.transportSecurity)
        assertTrue(switched.advancedParams.isEmpty())
        assertEquals("", switched.uuidOrPassword)
        assertFalse(NodeFormOptions.hasTransport(Protocol.SHADOWSOCKS))
    }

    @Test
    fun realityAndVisionRejectUnsupportedCombinationsBeforeSaving() = runBlocking {
        val reality = validForm().copy(transportSecurity = "reality", publicKey = "_".repeat(42) + "8", shortId = "abcd")
        assertNull(editor.validationIssue(reality))
        assertEquals(NodeFormIssueKind.INCOMPATIBLE, editor.validationIssue(reality.copy(network = "ws"))?.kind)
        assertFalse(editor.saveForm(0, reality.copy(network = "httpupgrade")))
        assertTrue(nodes.nodes.value.isEmpty())
        assertEquals("shortId", editor.validationIssue(reality.copy(shortId = "abc"))?.field)
        assertEquals("password (REALITY)", editor.validationIssue(reality.copy(publicKey = "invalid"))?.field)
        assertNotNull(editor.validationIssue(validForm().copy(flow = "xtls-rprx-vision", transportSecurity = "none", address = "192.0.2.1")))
        assertNull(editor.validationIssue(validForm().copy(flow = "xtls-rprx-vision", transportSecurity = "tls")))
    }

    @Test
    fun requiredEndpointsAuthenticationAndStructuredExtrasAreValidated() {
        for (port in listOf("", "0", "65536", "abc")) assertEquals("port", editor.validationIssue(validForm().copy(port = port))?.field)
        assertEquals("address", editor.validationIssue(validForm().copy(address = "https://example.com"))?.field)
        assertEquals("id", editor.validationIssue(validForm().copy(uuidOrPassword = ""))?.field)
        assertEquals("user", editor.validationIssue(validForm(Protocol.HTTP))?.field)
        assertNull(editor.validationIssue(validForm(Protocol.SOCKS).copy(uuidOrPassword = "")))
        assertEquals("xhttpSettings.extra", editor.validationIssue(validForm().copy(network = "xhttp", advancedParams = mapOf("extra" to "[]")))?.field)
        assertEquals("obfs-password", editor.validationIssue(validForm(Protocol.HYSTERIA2).copy(hysteria2Obfs = "salamander"))?.field)
        assertEquals("password", editor.validationIssue(validForm(Protocol.SHADOWSOCKS).copy(ssMethod = "2022-blake3-aes-128-gcm"))?.field)
    }

    @Test
    fun advancedFieldsAppearInTheActualOutboundAndCanBeCleared() {
        val form = validForm().copy(network = "xhttp", transportSecurity = "tls", advancedParams = mapOf(
            "mode" to "packet-up", "extra" to "{\"noSSEHeader\":true}", "alpn" to "h2,http/1.1",
            "vcn" to "cert.example", "pcs" to "a".repeat(64), "ech" to "example.com",
        ))
        val root = Json.parseToJsonElement(editor.previewOutbound(form)).jsonObject
        val stream = root["streamSettings"]!!.jsonObject
        assertEquals("packet-up", stream["xhttpSettings"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        assertTrue(stream["xhttpSettings"]!!.jsonObject["extra"]!!.jsonObject["noSSEHeader"]!!.jsonPrimitive.boolean)
        val tls = stream["tlsSettings"]!!.jsonObject
        assertEquals(listOf("h2", "http/1.1"), tls["alpn"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("cert.example", tls["verifyPeerCertByName"]!!.jsonPrimitive.content)
        assertEquals("a".repeat(64), tls["pinnedPeerCertSha256"]!!.jsonPrimitive.content)
        assertEquals("example.com", tls["echConfigList"]!!.jsonPrimitive.content)
        val imported = editor.parseForm("vless", editor.encodeUrl(form))
        val cleared = imported.copy(advancedParams = mapOf("alpn" to "", "extra" to ""))
        val clearedStream = Json.parseToJsonElement(editor.previewOutbound(cleared)).jsonObject["streamSettings"]!!.jsonObject
        assertNull(clearedStream["tlsSettings"]!!.jsonObject["alpn"])
        assertNull(clearedStream["xhttpSettings"]!!.jsonObject["extra"])
    }

    @Test
    fun grpcRawAndKcpFieldsReachTheirCorrespondingSettings() {
        fun stream(form: NodeEditForm) = Json.parseToJsonElement(editor.previewOutbound(form)).jsonObject["streamSettings"]!!.jsonObject
        val grpc = stream(validForm(Protocol.VMESS).copy(network = "grpc", grpcServiceName = "svc",
            advancedParams = mapOf("authority" to "cdn.example", "mode" to "multi")))["grpcSettings"]!!.jsonObject
        assertEquals("svc", grpc["serviceName"]!!.jsonPrimitive.content)
        assertEquals("cdn.example", grpc["authority"]!!.jsonPrimitive.content)
        assertTrue(grpc["multiMode"]!!.jsonPrimitive.boolean)
        val raw = stream(validForm().copy(wsPath = "/one,/two", wsHost = "cdn.example",
            advancedParams = mapOf("headerType" to "http")))["rawSettings"]!!.jsonObject
        val request = raw["header"]!!.jsonObject["request"]!!.jsonObject
        assertEquals(listOf("/one", "/two"), request["path"]!!.jsonArray.map { it.jsonPrimitive.content })
        val kcp = stream(validForm().copy(network = "kcp", advancedParams = mapOf("mtu" to "1350", "tti" to "50", "cwndMultiplier" to "2")))["kcpSettings"]!!.jsonObject
        assertEquals(1350, kcp["mtu"]!!.jsonPrimitive.int)
        assertEquals(50, kcp["tti"]!!.jsonPrimitive.int)
        assertEquals(2, kcp["cwndMultiplier"]!!.jsonPrimitive.int)
        assertNull(kcp["header"])
        assertNull(kcp["seed"])
    }

    @Test
    fun ipv6SurvivesEverySupportedFormEncoding() = runBlocking {
        for (protocol in Protocol.entries) {
            val form = validForm(protocol).copy(address = "2001:db8::1",
                username = if (protocol in setOf(Protocol.HTTP, Protocol.SOCKS)) "user" else "")
            assertTrue(editor.saveForm(0, form), protocol.name)
            assertEquals("2001:db8::1", nodes.nodes.value.last().address, protocol.name)
            val outbound = Json.parseToJsonElement(editor.previewOutbound(form)).jsonObject
            assertNotNull(outbound["settings"])
        }
    }

    @Test
    fun importedAliasesAndEmptyVmessTlsRemainEditable() {
        val payload = """{"add":"example.com","port":443,"id":"$id","net":"tcp","tls":""}"""
        val vmess = editor.parseForm("vmess", "vmess://" + Base64Compat.encode(payload.encodeToByteArray()))
        assertEquals("none", vmess.transportSecurity)
        assertNull(editor.validationIssue(vmess))
        val kcp = editor.parseForm("vless", "vless://$id@example.com:443?type=kcp&security=tls&path=legacy-seed")
        assertEquals(NodeFormIssueKind.LEGACY_KCP, editor.validationIssue(kcp)?.kind)
        val reality = editor.parseForm("vless", "vless://$id@example.com:443?security=reality&pbk=${"A".repeat(43)}&mldsa65Verify=legacy")
        assertEquals("legacy", reality.advancedValue("pqv"))
        val cleared = factory.vlessConfigParser.decodeProtocol(editor.encodeUrl(reality.copy(advancedParams = mapOf("pqv" to ""))))
        assertNull(cleared.param["pqv"])
        assertNull(cleared.param["mldsa65Verify"])
    }

    @Test
    fun vmessRealityRoundTripsCredentialsAndUsesCoreFieldNames() {
        val form = validForm(Protocol.VMESS).copy(transportSecurity = "reality", publicKey = "A".repeat(43), shortId = "abcd")
        val json = Json.parseToJsonElement(editor.previewOutbound(form)).jsonObject
        val reality = json["streamSettings"]!!.jsonObject["realitySettings"]!!.jsonObject
        assertEquals(form.publicKey, reality["password"]!!.jsonPrimitive.content)
        assertEquals("abcd", reality["shortId"]!!.jsonPrimitive.content)
        val imported = editor.parseForm("vmess", editor.encodeUrl(form))
        assertEquals(form.publicKey, imported.publicKey)
        assertEquals(form.shortId, imported.shortId)
    }

    @Test
    fun publicServersAndAdvancedSecurityFieldsFollowCoreValidation() {
        assertEquals(NodeFormIssueKind.SECURITY_REQUIRED, editor.validationIssue(validForm().copy(transportSecurity = "none"))?.kind)
        assertNull(editor.validationIssue(validForm().copy(address = "192.168.1.1", transportSecurity = "none")))
        assertNull(editor.validationIssue(validForm().copy(uuidOrPassword = id.replace("-", ""))))
        assertEquals("encryption", editor.validationIssue(validForm().copy(vlessEncryption = "made-up"))?.field)
        assertEquals("pinnedPeerCertSha256", editor.validationIssue(validForm().copy(advancedParams = mapOf("pcs" to "abcd")))?.field)
        assertEquals("mldsa65Verify", editor.validationIssue(validForm().copy(transportSecurity = "reality", publicKey = "A".repeat(43), advancedParams = mapOf("pqv" to "invalid")))?.field)
        assertEquals("tti", editor.validationIssue(validForm().copy(network = "kcp", advancedParams = mapOf("tti" to "1")))?.field)
    }

    @Test
    fun updatingEndpointKeepsStoredSummaryAndOutboundConsistent() = runBlocking {
        assertTrue(editor.saveForm(5, validForm().copy(address = "2001:db8::2")))
        assertEquals("2001:db8::2", nodes.updatedAddress)
        assertEquals("", nodes.updatedCountry)
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
    var updatedAddress: String? = null
    var updatedCountry: String? = null
    override suspend fun updateNode(id: Int, url: String, port: Int, remark: String?, address: String?, countryISO: String?) {
        updatedAddress = address
        updatedCountry = countryISO
    }
    override suspend fun updateSelectById(id: Int, selected: Boolean) = error("Unexpected mutation")
    override suspend fun updateFavoriteById(id: Int, favorite: Boolean) = error("Unexpected mutation")
    override suspend fun deleteLinkById(id: Int) = error("Unexpected mutation")
    override suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) = error("Unexpected mutation")
    override suspend fun deleteAllNodes() = error("Unexpected mutation")
}
