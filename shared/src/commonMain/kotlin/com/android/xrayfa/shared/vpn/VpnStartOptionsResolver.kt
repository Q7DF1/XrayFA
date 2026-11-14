package com.android.xrayfa.shared.vpn

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.config.JsonConfigError
import com.android.xrayfa.config.JsonConfigException
import com.android.xrayfa.config.NodePreProxyError
import com.android.xrayfa.config.NodePreProxyException
import com.android.xrayfa.config.NodePreProxyPolicy
import com.android.xrayfa.config.XDrivePreProxy
import com.android.xrayfa.config.XrayJson
import com.android.xrayfa.config.withNodeMux
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.model.isManagedXDrive
import com.android.xrayfa.model.jsonInboundTag
import com.android.xrayfa.model.requireXDriveReady
import com.android.xrayfa.model.serialization.OutboundObjectSerializer
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Resolves persisted node options for Android, iOS and offline delay probes. */
class VpnStartOptionsResolver(
    private val nodeRepository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val parserFactory: ParserFactory,
) {
    suspend fun resolve(): CoreStartOptions? =
        nodeRepository.querySelectedNode().first()?.let { resolveNode(it) }

    suspend fun resolveNode(node: Node): CoreStartOptions {
        node.requireXDriveReady()
        val subscription = if (node.isJsonConfig) null
            else subscriptionRepository.getSubscriptionById(node.subscriptionId).first()
        val preNodeId = node.preNodeId ?: subscription?.preNodeId
        val preNode = resolvePreNode(node.id, preNodeId)

        if (node.isJsonConfig) return resolveJsonNode(node, preNode)

        val nextNode = subscription?.nextNodeId
            ?.takeIf { it > 0 }
            ?.let { nodeRepository.loadLinksById(it).first() }
            ?.takeUnless { it.isJsonConfig }
        return CoreStartOptions(
            url = node.url,
            preUrl = preNode?.url,
            nextUrl = nextNode?.url,
            muxSettings = node.muxSettings,
            preMuxSettings = preNode?.muxSettings,
            nextMuxSettings = nextNode?.muxSettings,
        )
    }

    private suspend fun resolvePreNode(ownerId: Int, preNodeId: Int?): Node? {
        if (preNodeId == null || preNodeId == NodePreProxyPolicy.DISABLED) return null
        val hop = nodeRepository.loadLinksById(preNodeId).first()
        // The selected hop's subscription chain is intentionally not expanded.
        return NodePreProxyPolicy.requireHop(ownerId, preNodeId, hop)
    }

    private fun resolveJsonNode(node: Node, preNode: Node?): CoreStartOptions {
        val source = node.jsonData ?: throw JsonConfigException(JsonConfigError.INVALID_CONFIG)
        val config = if (preNode == null) source else {
            if (!node.isManagedXDrive) throw NodePreProxyException(NodePreProxyError.INVALID)
            val outbound = withNodeMux(
                parserFactory.getParser(preNode.url).parseOutbound(preNode.url),
                preNode.muxSettings,
            )
            val preOutbound = Json.parseToJsonElement(
                XrayJson.encodeToString(OutboundObjectSerializer, outbound),
            ).jsonObject
            XDrivePreProxy.attach(source, preOutbound)
        }
        return CoreStartOptions(url = node.url, jsonConfig = config, jsonInboundTag = node.jsonInboundTag)
    }
}
