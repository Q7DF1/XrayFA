package com.android.xrayfa.shared.vpn

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first

/**
 * Builds [CoreStartOptions] from selected node + subscription chain.
 * Mirrors Android [com.android.xrayfa.core.XrayBaseServiceManager.getConfigInformation].
 */
class VpnStartOptionsResolver(
    private val nodeRepository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend fun resolve(): CoreStartOptions? {
        val node = nodeRepository.querySelectedNode().first() ?: return null
        var preUrl: String? = null
        var nextUrl: String? = null
        val subscription = subscriptionRepository.getSubscriptionById(node.subscriptionId).first()
        subscription?.preNodeId?.let { preId ->
            preUrl = nodeRepository.loadLinksById(preId).first()?.url
        }
        subscription?.nextNodeId?.let { nextId ->
            nextUrl = nodeRepository.loadLinksById(nextId).first()?.url
        }
        return CoreStartOptions(url = node.url, preUrl = preUrl, nextUrl = nextUrl)
    }
}
