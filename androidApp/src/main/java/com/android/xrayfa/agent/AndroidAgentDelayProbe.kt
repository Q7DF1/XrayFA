package com.android.xrayfa.agent

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.repository.NodeRepository
import kotlinx.coroutines.flow.first

class AndroidAgentDelayProbe(
    private val nodeRepository: NodeRepository,
    private val parserFactory: ParserFactory,
    private val xrayCore: XrayCore,
) {
    suspend fun measure(nodeId: Int, testUrl: String): AgentDelayResult {
        val node = nodeRepository.loadLinksById(nodeId).first()
            ?: return AgentDelayResult(
                nodeId = nodeId,
                delayMs = null,
                error = AgentErrorCode.NODE_NOT_FOUND,
            )
        return try {
            val config = parserFactory.getParser(node.url).parse(CoreStartOptions(url = node.url))
            val delayMs = xrayCore.measureOutboundDelay(config, testUrl)
            if (delayMs <= 0L) {
                AgentDelayResult(nodeId = nodeId, delayMs = null, error = AgentErrorCode.NETWORK_ERROR)
            } else {
                AgentDelayResult(nodeId = nodeId, delayMs = delayMs)
            }
        } catch (_: Exception) {
            AgentDelayResult(nodeId = nodeId, delayMs = null, error = AgentErrorCode.NETWORK_ERROR)
        }
    }
}
