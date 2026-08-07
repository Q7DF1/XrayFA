package com.android.xrayfa.shared.subscription

/** KMP-safe subscription URL check (mirrors Android [validateUrl] behavior). */
fun validateSubscriptionUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return false

    val match =
        Regex(
            pattern = "^(https?)://([^/?#]+)",
            options = setOf(RegexOption.IGNORE_CASE),
        ).find(trimmed)
    return match != null && match.groupValues[2].isNotBlank()
}
