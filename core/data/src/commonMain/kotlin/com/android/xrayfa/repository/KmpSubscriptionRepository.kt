package com.android.xrayfa.repository

import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.database.dao.SubscriptionDao
import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.toDomain
import com.android.xrayfa.dto.toEntity
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.model.SubscriptionMeta
import com.android.xrayfa.network.SubscriptionFetcher
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.SubscriptionParser
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val XHWID = "x-hwid"

/** KMP [SubscriptionRepository] for Android and iOS. */
class KmpSubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
    private val subscriptionFetcher: SubscriptionFetcher,
    private val nodeRepository: NodeRepository,
    private val subscriptionParser: SubscriptionParser,
    private val parserFactory: ParserFactory,
    private val settingsRepository: SettingsRepository,
    private val nodeDao: NodeDao,
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

        val fetchResult = subscriptionStep(SubscriptionFailure.Stage.FETCH) {
            subscriptionFetcher.fetch(url, requestHeaders)
        }
        val subscriptionMeta = fetchResult.meta

        val content = fetchResult.body
        if (content.isBlank()) return subscriptionMeta

        val newNodes = withContext(Dispatchers.Default) {
            subscriptionStep(SubscriptionFailure.Stage.PARSE) {
                val urls = try {
                    subscriptionParser.parseUrl(content)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw SubscriptionParseFailure("decode_subscription", null, null, e)
                }
                urls.mapIndexed { index, rawUrl ->
                    val input = ParseLinkInput(
                        protocolPrefix = rawUrl.substringBefore("://"),
                        content = rawUrl,
                        selected = false,
                        subscriptionId = subscriptionId,
                    )
                    try {
                        parserFactory.getParser(input.content).preParse(input)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        throw SubscriptionParseFailure("parse_node", index + 1, input.protocolPrefix, e)
                    }
                }
            }
        }

        subscriptionStep(SubscriptionFailure.Stage.SAVE) {
            nodeDao.replaceSubscriptionNodes(subscriptionId, newNodes.map { it.toEntity() })
        }
        return subscriptionMeta
    }
}

private suspend fun <T> subscriptionStep(stage: SubscriptionFailure.Stage, block: suspend () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw SubscriptionFailure(stage, e)
    }
