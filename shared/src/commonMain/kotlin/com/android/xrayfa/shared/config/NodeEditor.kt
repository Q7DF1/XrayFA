package com.android.xrayfa.shared.config

import com.android.xrayfa.common.utils.Logger
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.model.protocol.protocolsPrefix
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.vpn.VpnController
import kotlinx.coroutines.flow.first

class NodeEditor(
    private val nodeRepository: NodeRepository,
    private val parserFactory: ParserFactory,
    private val vpnController: VpnController,
    private val logger: Logger,
) {
    suspend fun updateNode(
        nodeId: Int,
        remark: String,
        link: String,
    ): Boolean {
        val existing = nodeRepository.loadLinksById(nodeId).first() ?: return false
        val trimmedLink = link.trim()
        val trimmedRemark = remark.trim()

        if (trimmedLink.isBlank()) {
            return false
        }

        if (trimmedLink == existing.url) {
            nodeRepository.updateNode(
                id = nodeId,
                url = existing.url,
                port = existing.port,
                remark = trimmedRemark.ifBlank { existing.remark },
            )
            return true
        }

        val protocolPrefix = trimmedLink.substringBefore("://").lowercase()
        if (!protocolsPrefix.contains(protocolPrefix)) {
            logger.i(TAG, "Unsupported protocol prefix: $protocolPrefix")
            return false
        }

        val parsed =
            parserFactory.getParser(trimmedLink).preParse(
                ParseLinkInput(
                    protocolPrefix = protocolPrefix,
                    content = trimmedLink,
                    subscriptionId = existing.subscriptionId,
                ),
            )

        val updated =
            parsed.copy(
                id = existing.id,
                remark = trimmedRemark.ifBlank { parsed.remark },
                favorite = existing.favorite,
                selected = existing.selected,
                isPreNode = existing.isPreNode,
                isNextNode = existing.isNextNode,
                subscriptionId = existing.subscriptionId,
            )

        nodeRepository.deleteLinkById(existing.id)
        nodeRepository.addNode(updated)
        vpnController.restartIfNeeded()
        return true
    }

    suspend fun deleteNode(nodeId: Int) {
        val selectedId = nodeRepository.querySelectedNode().first()?.id
        nodeRepository.deleteLinkById(nodeId)
        if (selectedId == nodeId) {
            vpnController.restartIfNeeded()
        }
    }

    private companion object {
        const val TAG = "NodeEditor"
    }
}
