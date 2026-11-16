package com.android.xrayfa.shared.config

import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.common.utils.UrlCodec
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isManagedXDrive
import com.android.xrayfa.model.configurationShareLink
import com.android.xrayfa.model.canShareConfiguration
import com.android.xrayfa.config.XDriveForm
import com.android.xrayfa.model.protocol.Protocol
import com.android.xrayfa.parser.*
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.shared.platform.ClipboardReader
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.VpnState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class NodeFormTransportTest {
    private val factory = testParserFactory()
    private val nodes = FormTestNodes()
    private val logger = object : Logger {
        override fun i(tag: String, message: String) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
    private var restarts = 0
    private val vpn = object : VpnController {
        override val state = MutableStateFlow<VpnState>(VpnState.Disconnected)
        override val connectError = MutableStateFlow<String?>(null)
        override suspend fun connect() = false
        override fun disconnect() = Unit
        override suspend fun restartIfNeeded() { restarts++ }
    }
    private val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    private val subscriptionRepository = object : SubscriptionRepository {
        override val allSubscriptions = subscriptions
        override fun getSubscriptionById(id: Int) = subscriptions.map { it.find { sub -> sub.id == id } }
        override suspend fun addSubscription(subscription: Subscription): Long = error("Unexpected mutation")
        override suspend fun deleteSubscription(subscription: Subscription) = error("Unexpected mutation")
        override suspend fun updateSubscription(subscription: Subscription) = error("Unexpected mutation")
        override suspend fun fetchAndSaveNodes(url: String, subscriptionId: Int, extraHeaders: Map<String, String>) = error("Unexpected mutation")
    }
    private val editor = NodeFormEditor(factory, nodes, vpn, logger, subscriptionRepository)
    private val id = "00000000-0000-0000-0000-000000000001"

    private fun proxyNode(number: Int, pre: Int? = null) = Node(id = number, protocolPrefix = "socks",
        address = "proxy$number.example", port = 1080, subscriptionId = 7,
        url = "socks://proxy$number.example:1080", preNodeId = pre)

    @Test fun individualPreNodePersistsReopensAndOverridesSubscription() = runBlocking {
        val original = proxyNode(10).copy(selected = true, favorite = true)
        val local = proxyNode(20)
        val inherited = proxyNode(30)
        nodes.nodes.value = listOf(original, local, inherited)
        subscriptions.value = listOf(Subscription(id = 7, mark = "sub", url = "https://example.com/sub", preNodeId = 30))
        val resolver = com.android.xrayfa.shared.vpn.VpnStartOptionsResolver(nodes, subscriptionRepository, factory)
        assertEquals(inherited.url, resolver.resolve()!!.preUrl)
        val form = editor.parseForm("socks", original.url).copy(preNodeId = local.id)
        assertTrue(editor.saveForm(original.id, form))
        val saved = nodes.nodes.value.first()
        assertTrue(saved.selected && saved.favorite)
        assertEquals(20, saved.preNodeId)
        assertEquals(20, editor.parseForm("socks", saved.url, preNodeId = saved.preNodeId).preNodeId)
        val options = resolver.resolve()!!
        assertEquals(local.url, options.preUrl)
        val runtime = Json.parseToJsonElement(factory.getParser(options.url).parse(options)).jsonObject
        val main = runtime["outbounds"]!!.jsonArray.map { it.jsonObject }.first { it["tag"] == JsonPrimitive("proxy") }
        assertEquals(JsonPrimitive("pre-node"), main["streamSettings"]!!.jsonObject["sockopt"]!!.jsonObject["dialerProxy"])
        assertTrue(editor.saveForm(10, form.copy(preNodeId = -1)))
        assertNull(resolver.resolve()!!.preUrl)
        assertTrue(editor.saveForm(10, form.copy(preNodeId = null)))
        assertEquals(inherited.url, resolver.resolve()!!.preUrl)
    }

    @Test fun preNodeRejectsSelfMissingJsonAndNestedHopsWithoutWriting() = runBlocking {
        val original = proxyNode(10).copy(selected = true)
        val json = proxyNode(40).copy(protocolPrefix = "json-config")
        nodes.nodes.value = listOf(original, proxyNode(20, 30), proxyNode(30), json)
        for (pre in listOf(10, 999, 20, 40, 0)) {
            assertFalse(editor.saveForm(10, editor.parseForm("socks", original.url).copy(preNodeId = pre)))
            assertEquals("PRE_NODE_INVALID", editor.lastSaveError)
            assertEquals(original, nodes.nodes.value.first())
        }
        val resolver = com.android.xrayfa.shared.vpn.VpnStartOptionsResolver(nodes, subscriptionRepository, factory)
        nodes.nodes.value = listOf(original.copy(preNodeId = 999))
        assertFailsWith<IllegalArgumentException> { resolver.resolve() }
        nodes.nodes.value = listOf(original.copy(preNodeId = 20), proxyNode(20, 10))
        assertFailsWith<IllegalArgumentException> { resolver.resolve() }
        Unit
    }

    @Test fun xdrivePreNodeIsInjectedOnlyAtRuntimeAndEditingHopRestartsConnection() = runBlocking {
        val hop = proxyNode(20)
        nodes.nodes.value = listOf(hop)
        assertTrue(editor.saveForm(0, xdriveForm().copy(preNodeId = 20)))
        val saved = nodes.nodes.value.last()
        nodes.nodes.value = listOf(hop, saved.copy(selected = true))
        val resolver = com.android.xrayfa.shared.vpn.VpnStartOptionsResolver(nodes, subscriptionRepository, factory)
        val result = Json.parseToJsonElement(resolver.resolve()!!.jsonConfig!!).jsonObject
        assertEquals(2, result["outbounds"]!!.jsonArray.size)
        assertEquals(JsonPrimitive("xrayfa-pre"), result["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject["sockopt"]!!.jsonObject["dialerProxy"])
        assertFalse(saved.jsonData!!.contains("xrayfa-pre"))
        val reopened = editor.parseForm(saved.protocolPrefix, saved.jsonData, preNodeId = saved.preNodeId)
        assertEquals(20, reopened.preNodeId)
        assertFalse(saved.configurationShareLink()!!.contains("proxy20"))
        assertTrue(editor.saveForm(hop.id, editor.parseForm("socks", hop.url).copy(remarks = "Changed hop")))
        assertEquals(1, restarts)
        assertFalse(editor.saveForm(hop.id, xdriveForm()))
        assertEquals("PRE_NODE_INVALID", editor.lastSaveError)
    }

    @Test fun muxPersistsReopensPreviewsAndCanBeRemoved() = runBlocking {
        val original = Node(id = 12, protocolPrefix = "vless", address = "example.com", port = 443,
            subscriptionId = 3, selected = true, favorite = true,
            url = "vless://$id@example.com:443?security=tls")
        nodes.nodes.value = listOf(original)
        val form = editor.parseForm("vless", original.url).copy(mux = NodeMuxForm("4", "16", "allow"))
        assertTrue(editor.saveForm(12, form))
        val saved = nodes.nodes.value.single()
        assertTrue(saved.favorite && saved.selected)
        assertEquals(3, saved.subscriptionId)
        val reopened = editor.parseForm("vless", saved.url, muxSettings = saved.muxSettings)
        assertEquals(form.mux, reopened.mux)
        val preview = Json.parseToJsonElement(editor.previewOutbound(reopened)).jsonObject
        assertEquals(4, preview["mux"]!!.jsonObject["concurrency"]!!.jsonPrimitive.int)
        assertTrue(editor.saveForm(12, reopened.copy(mux = null)))
        assertNull(nodes.nodes.value.single().muxSettings)
        assertNull(Json.parseToJsonElement(editor.previewOutbound(reopened.copy(mux = null))).jsonObject["mux"])
    }

    @Test fun muxValidatesInputAndNormalizesVision() {
        val base = editor.parseForm("vless", "vless://$id@example.com:443?security=tls")
        assertNotNull(editor.validationIssue(base.copy(mux = NodeMuxForm("129"))))
        assertNotNull(editor.validationIssue(base.copy(mux = NodeMuxForm(xudpConcurrency = "bad"))))
        assertNotNull(editor.validationIssue(base.copy(network = "xhttp", mux = NodeMuxForm())))
        val vision = base.copy(flow = "xtls-rprx-vision", mux = NodeMuxForm())
        assertNull(editor.validationIssue(vision))
        val mux = Json.parseToJsonElement(editor.previewOutbound(vision)).jsonObject["mux"]!!.jsonObject
        assertEquals(-1, mux["concurrency"]!!.jsonPrimitive.int)
        assertEquals(8, mux["xudpConcurrency"]!!.jsonPrimitive.int)
    }

    @Test fun switchingToXdriveAppliesItsMuxAndFlowRequirements() {
        val base = xdriveForm().copy(network = "raw", flow = "xtls-rprx-vision", mux = NodeMuxForm("-1", "-1", "skip"))
        val drive = base.selectNetwork("xdrive")
        assertEquals("", drive.flow)
        assertEquals(NodeMuxForm("8", "8", "reject"), drive.mux)
        assertNull(editor.validationIssue(drive))
        val custom = base.copy(mux = NodeMuxForm("4", "16", "allow")).selectNetwork("xdrive")
        assertEquals(NodeMuxForm("4", "16", "allow"), custom.mux)
        assertNull(base.selectNetwork("splithttp").mux)
    }

    @Test fun subscriptionTransportChangeDoesNotBlockEditingWithDormantMux() {
        val mux = com.android.xrayfa.config.NodeMuxConfig.encode(com.android.xrayfa.model.MuxObject())
        val xhttp = editor.parseForm("vless", "vless://$id@example.com:443?security=tls&type=xhttp", muxSettings = mux)
        assertNull(xhttp.mux)
        assertNull(editor.validationIssue(xhttp))
        assertNull(editor.parseForm("hy2", "hy2://password@example.com:443", muxSettings = mux).mux)
    }

    @Test fun editingAnActiveChainHopRestartsOnlyTheAffectedConnection() = runBlocking {
        val link = "vless://$id@example.com:443?security=tls"
        val selected = Node(id = 1, protocolPrefix = "vless", address = "example.com", port = 443,
            subscriptionId = 7, selected = true, url = link)
        val hop = selected.copy(id = 2, selected = false, subscriptionId = -1)
        nodes.nodes.value = listOf(selected, hop)
        subscriptions.value = listOf(Subscription(id = 7, mark = "active", url = "https://example.com/sub", preNodeId = 2))
        val form = editor.parseForm("vless", link).copy(mux = NodeMuxForm("4"))
        assertTrue(editor.saveForm(2, form))
        assertEquals(1, restarts)
        subscriptions.value = listOf(Subscription(id = 7, mark = "active", url = "https://example.com/sub", nextNodeId = 2))
        assertTrue(editor.saveForm(2, form))
        assertEquals(2, restarts)
        subscriptions.value = listOf(Subscription(id = 8, mark = "inactive", url = "https://example.com/sub", preNodeId = 2))
        assertTrue(editor.saveForm(2, form))
        assertEquals(2, restarts)
    }

    @Test fun xdriveAddressAndPortCanBeEditedAndReopened() = runBlocking {
        val form = xdriveForm().copy(address = "192.0.2.10", port = "9000")
        assertTrue(editor.saveForm(0, form))
        val saved = nodes.nodes.value.single()
        val reopened = editor.parseForm(saved.protocolPrefix, saved.jsonData, saved.remark)
        assertEquals("192.0.2.10", reopened.address)
        assertEquals("9000", reopened.port)
        assertEquals("port", editor.validationIssue(form.copy(port = ""))?.field)
        assertEquals("address", editor.validationIssue(form.copy(address = ""))?.field)
    }

    @Test fun xdriveMuxRemainsRequiredAndCanBeTuned() {
        val form = xdriveForm().copy(mux = NodeMuxForm("4", "16", "allow"))
        assertNull(editor.validationIssue(form))
        assertEquals(4, form.xdriveForm().build()["outbounds"]!!.jsonArray[0].jsonObject["mux"]!!.jsonObject["concurrency"]!!.jsonPrimitive.int)
        assertNotNull(editor.validationIssue(form.copy(mux = NodeMuxForm("-1"))))
        assertNotNull(editor.validationIssue(form.copy(mux = NodeMuxForm(udp443 = "skip"))))
        assertTrue(xdriveForm().xdriveForm().muxConfig().enabled)
    }

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
        nodes.nodes.value = listOf(Node(id = 5, protocolPrefix = "vless", address = "old.example", port = 443,
            subscriptionId = 0, url = "vless://$id@old.example:443?security=tls"))
        assertTrue(editor.saveForm(5, validForm().copy(address = "2001:db8::2")))
        assertEquals("2001:db8::2", nodes.updatedAddress)
        assertEquals("", nodes.updatedCountry)
        assertEquals("2001:db8::2", nodes.nodes.value.single().address)
    }

    private fun xdriveForm() = validForm().copy(address = "127.0.0.1", port = "443", network = "xdrive",
        vlessEncryption = "mlkem768x25519plus.native.1rtt.${"A".repeat(43)}",
        remarks = "Local storage node", xdrive = XDriveForm(service = "local", remoteFolder = "/tmp/xdrive-test"))

    @Test fun xdriveIsAVlessTransportWithNoShareOrPreviewEncoder() {
        assertTrue("xdrive" in NodeFormOptions.networks(Protocol.VLESS))
        assertFalse("xdrive" in NodeFormOptions.networks(Protocol.VMESS))
        assertFalse("xdrive" in NodeFormOptions.networks(Protocol.TROJAN))
        val form = xdriveForm()
        assertNull(editor.validationIssue(form))
        assertFailsWith<IllegalArgumentException> { editor.encodeUrl(form) }
        assertFailsWith<IllegalArgumentException> { editor.previewOutbound(form) }
        assertNotNull(editor.validationIssue(form.copy(selectedProtocol = Protocol.TROJAN)))
        assertEquals("encryption", editor.validationIssue(form.copy(vlessEncryption = "none"))?.field)
        assertEquals("flow", editor.validationIssue(form.copy(flow = "xtls-rprx-vision"))?.field)
    }

    @Test fun inlineVlessAndTlsFieldsRoundTripThroughLocalNodeStorage() = runBlocking {
        val form = xdriveForm().copy(sni = "storage.example", fingerprint = "firefox",
            advancedParams = mapOf("alpn" to "h3", "pcs" to "a".repeat(64), "vcn" to "storage.example", "ech" to "storage.example"))
        assertTrue(editor.saveForm(0, form))
        val saved = nodes.nodes.value.single()
        assertEquals(0, restarts)
        assertTrue(saved.isManagedXDrive)
        assertTrue(saved.canShareConfiguration)
        assertFalse(saved.url.contains("storage.example"))
        val reopened = editor.parseForm(saved.protocolPrefix, saved.jsonData, saved.remark)
        assertEquals("xdrive", reopened.network)
        assertEquals(form.uuidOrPassword, reopened.uuidOrPassword)
        assertEquals(form.vlessEncryption, reopened.vlessEncryption)
        assertEquals(form.fingerprint, reopened.fingerprint)
        assertEquals(form.advancedParams, reopened.advancedParams)
        assertEquals("/tmp/xdrive-test", reopened.xdrive.remoteFolder)
    }

    @Test fun transportConversionPreservesRecordIdentityAndClearsStoredCloudDataWhenLeavingXdrive() = runBlocking {
        val original = Node(id = 9, protocolPrefix = "vless", address = "example.com", port = 443,
            subscriptionId = 123, favorite = true, selected = true, isPreNode = true, isNextNode = true, url = "vless://$id@example.com:443?security=tls")
        nodes.nodes.value = listOf(original)
        val secret = "fixture-cloud-secret"
        assertTrue(editor.saveForm(9, xdriveForm().copy(xdrive = XDriveForm(service = "google-drive", remoteFolder = "fixture-folder", secrets = listOf(secret, secret, secret)))))
        val local = nodes.nodes.value.single()
        assertEquals(9, local.id)
        assertTrue(local.isManagedXDrive && local.favorite && local.selected)
        assertEquals(123, local.subscriptionId)
        assertTrue(local.isPreNode && local.isNextNode)
        assertEquals(1, restarts)
        val ordinary = editor.parseForm(local.protocolPrefix, local.jsonData).copy(network = "raw", address = "example.com", port = "443")
        assertTrue(editor.saveForm(9, ordinary))
        val converted = nodes.nodes.value.single()
        assertEquals("vless", converted.protocolPrefix)
        assertNull(converted.jsonData)
        assertTrue(converted.canShareConfiguration && converted.favorite && converted.selected)
        assertEquals(123, converted.subscriptionId)
        assertTrue(converted.isPreNode && converted.isNextNode)
        assertEquals(2, restarts)
        assertFalse(converted.url.contains("xdrive"))
        assertFalse(converted.url.contains(secret))
    }

    @Test fun xdriveConversionNeverRemovesConfiguredProxyChainHops() = runBlocking {
        val original = Node(id = 9, protocolPrefix = "vless", address = "example.com", port = 443,
            subscriptionId = 7, url = "vless://$id@example.com:443?security=tls")
        nodes.nodes.value = listOf(original)
        for (subscription in listOf(
            Subscription(id = 8, mark = "chain", url = "https://example.com/sub", preNodeId = 9),
            Subscription(id = 8, mark = "chain", url = "https://example.com/sub", nextNodeId = 9),
            Subscription(id = 7, mark = "own chain", url = "https://example.com/sub", preNodeId = 3),
        )) {
            subscriptions.value = listOf(subscription)
            val candidate = original.copy(selected = subscription.id == original.subscriptionId)
            nodes.nodes.value = listOf(candidate)
            assertFalse(editor.saveForm(9, xdriveForm()))
            assertEquals("XDRIVE_CHAIN", editor.lastSaveError)
            assertEquals(listOf(candidate), nodes.nodes.value)
            assertEquals(0, restarts)
        }
    }

}

private class FormTestNodes : NodeRepository {
    val nodes = MutableStateFlow<List<Node>>(emptyList())
    override val allNodes = nodes
    override val favorites = nodes.map { it.filter(Node::favorite) }
    override suspend fun addNode(vararg nodes: Node) { this.nodes.value += nodes }
    override suspend fun deleteLink(link: Node) = error("Unexpected mutation")
    override fun loadLinksById(id: Int) = nodes.map { list -> list.find { it.id == id } }
    override suspend fun clearSelection() = error("Unexpected mutation")
    override fun querySelectedNode() = nodes.map { it.find(Node::selected) }
    override fun queryPreNode() = flowOf<Node?>(null)
    override fun queryNextNode() = flowOf<Node?>(null)
    var updatedAddress: String? = null
    var updatedCountry: String? = null
    override suspend fun updateNode(id: Int, url: String, port: Int, remark: String?, address: String?, countryISO: String?) {
        updatedAddress = address
        updatedCountry = countryISO
    }
    override suspend fun updateNodeWithOptions(node: Node) {
        updatedAddress = node.address
        updatedCountry = node.countryISO
        nodes.value = nodes.value.map { if (it.id == node.id) node else it }
    }
    override suspend fun updateNodeConfiguration(id: Int, protocolPrefix: String, url: String, jsonData: String?, address: String, port: Int, remark: String?) {
        nodes.value = nodes.value.map { if (it.id == id) it.copy(protocolPrefix = protocolPrefix, url = url, jsonData = jsonData, address = address, port = port, remark = remark) else it }
    }
    override suspend fun updateSelectById(id: Int, selected: Boolean) = error("Unexpected mutation")
    override suspend fun updateFavoriteById(id: Int, favorite: Boolean) = error("Unexpected mutation")
    override suspend fun deleteLinkById(id: Int) = error("Unexpected mutation")
    override suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) = error("Unexpected mutation")
    override suspend fun deleteAllNodes() = error("Unexpected mutation")
}
