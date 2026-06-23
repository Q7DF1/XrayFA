package com.android.xrayfa.repository

import android.util.Log
import com.android.xrayfa.common.di.qualifier.ShortTime
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.dao.SubscriptionDao
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Subscription
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.parser.SubscriptionParser
import com.android.xrayfa.utils.HttpResponseUtils
import com.android.xrayfa.utils.SubscriptionMeta
import com.android.xrayfa.utils.SubscriptionUserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val XHWID = "x-hwid"
private const val TAG = "SubscriptionRepository"

@Singleton
class SubscriptionRepository
@Inject constructor(
    val subscriptionDao: SubscriptionDao,
    @ShortTime val okHttp: OkHttpClient,
    val nodeRepository: NodeRepository,
    val subscriptionParser: SubscriptionParser,
    val parserFactory: ParserFactory,
    val settingsRepository: SettingsRepository,
) {

    val allSubscriptions = subscriptionDao.getALLSubscriptions()

    suspend fun addSubscription(subscription: Subscription) {
        subscriptionDao.addSubscription(subscription)
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        subscriptionDao.deleteSubscription(subscription)
    }

    suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.updateSubscription(subscription)
    }

    fun getSubscriptionById(id: Int): Flow<Subscription?> {
        return subscriptionDao.selectSubscriptionById(id)
    }

    suspend fun fetchAndSaveNodes(
        url: String,
        subscriptionId: Int,
        extraHeaders: Map<String, String> = emptyMap()
    ): SubscriptionMeta {
        val currentSettings = settingsRepository.settingsFlow.first()

        val requestBuilder = Request.Builder()
            .get()
            .url(url)
            .addHeader(XHWID, currentSettings.hwid)

        extraHeaders.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val response = okHttp.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful) {
            throw IOException("HTTP error: ${response.code}")
        }

        val subscriptionMeta = HttpResponseUtils.parseSubscriptionMeta(response)

        val content = response.body?.string() ?: return subscriptionMeta
        if (content.isBlank()) return subscriptionMeta

        val urls = subscriptionParser.parseUrl(content)
        nodeRepository.deleteLinkBySubscriptionId(subscriptionId)

        val newNodes = urls.map { rawUrl ->
            Log.i(TAG, "fetchAndSaveNodes: protocol=${rawUrl.substringBefore("://")}")
            val link = Link(
                protocolPrefix = rawUrl.substringBefore("://"),
                content = rawUrl,
                selected = false,
                subscriptionId = subscriptionId,
            )
            parserFactory.getParser(link.content).preParse(link)
        }

        nodeRepository.addNode(*newNodes.toTypedArray())
        return subscriptionMeta
    }
}