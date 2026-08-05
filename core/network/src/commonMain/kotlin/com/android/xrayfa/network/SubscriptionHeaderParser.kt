package com.android.xrayfa.network

import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.model.SubscriptionMeta
import com.android.xrayfa.model.SubscriptionUserInfo

object SubscriptionHeaderParser {

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
            Base64Compat.decode(encoded).decodeToString()
        } catch (_: Exception) {
            value
        }
    }

    fun parseSubscriptionUserInfo(header: String): SubscriptionUserInfo? {
        return try {
            val map = parseKeyValue(header)
            SubscriptionUserInfo(
                upload = map["upload"]?.toLongOrNull() ?: 0L,
                download = map["download"]?.toLongOrNull() ?: 0L,
                total = map["total"]?.toLongOrNull() ?: 0L,
                expire = map["expire"]?.toLongOrNull(),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseSubscriptionMeta(headers: Map<String, List<String>>): SubscriptionMeta {
        fun header(name: String): String? = headers[name]?.firstOrNull()

        val userInfoHeader = header(HEADER_SUBSCRIPTION_USERINFO)
        return SubscriptionMeta(
            announce = header(HEADER_ANNOUNCE)?.let(::decodeBase64Header),
            profileTitle = header(HEADER_PROFILE_TITLE)?.let(::decodeBase64Header),
            profileUpdateIntervalHours = header(HEADER_PROFILE_UPDATE_INTERVAL)?.trim()?.toIntOrNull(),
            profileWebPageUrl = header(HEADER_PROFILE_WEB_PAGE_URL),
            routing = header(HEADER_ROUTING),
            routingEnable = header(HEADER_ROUTING_ENABLE)?.trim()?.lowercase()?.toBooleanStrictOrNull(),
            supportUrl = header(HEADER_SUPPORT_URL),
            servedBy = header(HEADER_X_SERVED_BY),
            userInfo = userInfoHeader?.let(::parseSubscriptionUserInfo),
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
