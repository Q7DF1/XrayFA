package com.android.xrayfa.repository

import android.util.Log
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.database.dao.SubscriptionDao
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.toDomain
import com.android.xrayfa.dto.toEntity
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.model.SubscriptionMeta
import com.android.xrayfa.network.SubscriptionFetcher
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.SubscriptionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val XHWID = "x-hwid"
private const val TAG = "AndroidSubscriptionRepository"

class AndroidSubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
    private val subscriptionFetcher: SubscriptionFetcher,
    private val nodeRepository: NodeRepository,
    private val subscriptionParser: SubscriptionParser,
    private val parserFactory: ParserFactory,
    private val settingsRepository: SettingsRepository,
) : SubscriptionRepository {

    override val allSubscriptions: Flow<List<Subscription>> =
        subscriptionDao.getALLSubscriptions().map { list -> list.map { it.toDomain() } }

    override suspend fun addSubscription(subscription: Subscription): Long {
        return subscriptionDao.addSubscription(subscription.toEntity())
    }

    override suspend fun deleteSubscription(subscription: Subscription) {
        nodeRepository.deleteLinkBySubscriptionId(subscription.id)
        subscriptionDao.deleteSubscription(subscription.toEntity())
    }

    override suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.updateSubscription(subscription.toEntity())
    }

    override fun getSubscriptionById(id: Int): Flow<Subscription?> {
        return subscriptionDao.selectSubscriptionById(id).map { it?.toDomain() }
    }

    override suspend fun fetchAndSaveNodes(
        url: String,
        subscriptionId: Int,
        extraHeaders: Map<String, String>,
    ): SubscriptionMeta {
        if (subscriptionId > 0) {
            nodeRepository.deleteLinkBySubscriptionId(0)
        }

        val currentSettings = settingsRepository.settingsFlow.first()
        val requestHeaders = buildMap {
            if (currentSettings.sendHwid) {
                put(XHWID, currentSettings.hwid)
            }
            putAll(extraHeaders)
        }

        val fetchResult = subscriptionFetcher.fetch(url, requestHeaders)
        val subscriptionMeta = fetchResult.meta

        val content = fetchResult.body
        if (content.isBlank()) return subscriptionMeta

        val urls = subscriptionParser.parseUrl(content)
        nodeRepository.deleteLinkBySubscriptionId(subscriptionId)

        val newNodes = urls.map { rawUrl ->
            Log.i(TAG, "fetchAndSaveNodes: protocol=${rawUrl.substringBefore("://")}")
            val input = ParseLinkInput(
                protocolPrefix = rawUrl.substringBefore("://"),
                content = rawUrl,
                selected = false,
                subscriptionId = subscriptionId,
            )
            parserFactory.getParser(input.content).preParse(input)
        }

        nodeRepository.addNode(*newNodes.toTypedArray())
        return subscriptionMeta
    }
}
