package com.android.xrayfa.utils

import okhttp3.Response
import java.util.Base64

data class SubscriptionUserInfo(
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long?
)

data class SubscriptionMeta(
    val announce: String?,
    var profileTitle: String?,
    val profileUpdateIntervalHours: Int?,
    val profileWebPageUrl: String?,
    val routing: String?,
    val routingEnable: Boolean?,
    val supportUrl: String?,
    val servedBy: String?,
    val userInfo: SubscriptionUserInfo?
)

object HttpResponseUtils {

    private const val PREFIX_BASE64 = "base64:"

    private const val HEADER_ANNOUNCE = "Announce"
    private const val HEADER_PROFILE_TITLE = "Profile-Title"
    private const val HEADER_PROFILE_UPDATE_INTERVAL = "Profile-Update-Interval"
    private const val HEADER_PROFILE_WEB_PAGE_URL = "Profile-Web-Page-Url"
    private const val HEADER_ROUTING = "Routing"
    private const val HEADER_ROUTING_ENABLE = "Routing-Enable"
    private const val HEADER_SUBSCRIPTION_USERINFO = "Subscription-Userinfo"
    private const val HEADER_SUPPORT_URL = "Support-Url"
    private const val HEADER_X_SERVED_BY = "X-Served-By"

    fun decodeBase64Header(value: String): String {
        if (!value.startsWith(PREFIX_BASE64)) return value
        return try {
            val encoded = value.removePrefix(PREFIX_BASE64)
            String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        } catch (e: Exception) {
            value
        }
    }

    fun parseSubscriptionUserInfo(response: Response): SubscriptionUserInfo? {
        val header = response.header(HEADER_SUBSCRIPTION_USERINFO) ?: return null
        return parseSubscriptionUserInfo(header)
    }

    fun parseSubscriptionUserInfo(header: String): SubscriptionUserInfo? {
        return try {
            val map = parseKeyValue(header)
            SubscriptionUserInfo(
                upload = map["upload"]?.toLongOrNull() ?: 0L,
                download = map["download"]?.toLongOrNull() ?: 0L,
                total = map["total"]?.toLongOrNull() ?: 0L,
                expire = map["expire"]?.toLongOrNull()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseSubscriptionMeta(response: Response): SubscriptionMeta {
        return SubscriptionMeta(
            announce = response.header(HEADER_ANNOUNCE)
                ?.let { decodeBase64Header(it) },
            profileTitle = response.header(HEADER_PROFILE_TITLE)
                ?.let { decodeBase64Header(it) },
            profileUpdateIntervalHours = response.header(HEADER_PROFILE_UPDATE_INTERVAL)
                ?.trim()?.toIntOrNull(),
            profileWebPageUrl = response.header(HEADER_PROFILE_WEB_PAGE_URL),
            routing = response.header(HEADER_ROUTING),
            routingEnable = response.header(HEADER_ROUTING_ENABLE)
                ?.trim()?.lowercase()?.toBooleanStrictOrNull(),
            supportUrl = response.header(HEADER_SUPPORT_URL),
            servedBy = response.header(HEADER_X_SERVED_BY),
            userInfo = parseSubscriptionUserInfo(response)
        )
    }

    private fun parseKeyValue(header: String): Map<String, String> =
        header.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key.trim() to value.trim()
            }
}
