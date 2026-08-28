package com.android.xrayfa.shared.ui.settings

/**
 * KMP settings field validators aligned with Android [validateIpv4List] / [validatePort] etc.
 * Error strings come from [SettingsUiLabels] so platforms can localize via labels.
 */
object SettingsValidators {
    private fun formatIndexed(
        template: String,
        index: Int,
        value: String = "",
    ): String =
        template
            .replace("%1\$d", index.toString())
            .replace("%2\$s", value)
            .replace("%d", index.toString())
            .replace("%s", value)

    fun validatePort(
        input: String,
        labels: SettingsUiLabels,
    ): String? {
        val port = input.toIntOrNull() ?: return labels.portInvalid
        return if (port in 1..65535) null else labels.portInvalid
    }

    fun validateNonEmpty(
        input: String,
        labels: SettingsUiLabels,
    ): String? = if (input.isBlank()) labels.cannotBeEmpty else null

    fun validateIpv4List(
        input: String,
        labels: SettingsUiLabels,
    ): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return labels.ipv4Empty

        val ipv4Regex =
            Regex("""^(?:25[0-5]|2[0-4]\d|1?\d{1,2})(?:\.(?:25[0-5]|2[0-4]\d|1?\d{1,2})){3}$""")

        val parts = trimmed.split(",")
        if (parts.isEmpty()) return labels.ipv4Empty

        val seen = mutableSetOf<String>()
        for ((index, raw) in parts.withIndex()) {
            val part = raw.trim()
            if (part.isEmpty()) {
                return formatIndexed(labels.ipv4ItemEmpty, index + 1)
            }
            if (!ipv4Regex.matches(part)) {
                return formatIndexed(labels.ipv4Invalid, index + 1, part)
            }
            if (!seen.add(part)) {
                return formatIndexed(labels.ipv4Duplicate, index + 1, part)
            }
        }
        return null
    }

    fun validateIpv6List(
        input: String,
        labels: SettingsUiLabels,
    ): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return labels.ipv6Empty

        val ipv6Regex =
            Regex(
                "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
                    "([0-9a-fA-F]{1,4}:){1,7}:|" +
                    "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
                    "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
                    "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
                    "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
                    "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
                    "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
                    ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +
                    "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|" +
                    "::(ffff(:0{1,4}){0,1}:){0,1}" +
                    "((25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9])\\.){3,3}" +
                    "(25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9])|" +
                    "([0-9a-fA-F]{1,4}:){1,4}:" +
                    "((25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9])\\.){3,3}" +
                    "(25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9]))$",
            )

        val parts = trimmed.split(",")
        if (parts.isEmpty()) return labels.ipv6Empty

        val seen = mutableSetOf<String>()
        for ((index, raw) in parts.withIndex()) {
            val part = raw.trim()
            if (part.isEmpty()) {
                return formatIndexed(labels.ipv6ItemEmpty, index + 1)
            }
            if (!ipv6Regex.matches(part)) {
                return formatIndexed(labels.ipv6Invalid, index + 1, part)
            }
            if (!seen.add(part)) {
                return formatIndexed(labels.ipv6Duplicate, index + 1, part)
            }
        }
        return null
    }

    fun validateSocksCredential(
        input: String,
        labels: SettingsUiLabels,
        isPassword: Boolean,
    ): String? {
        val trimmed = input.trim()
        val credentialsRegex = Regex("^[\\x21-\\x7E]+$")

        if (trimmed.isEmpty()) {
            return if (isPassword) labels.socksPasswordEmpty else labels.socksUsernameEmpty
        }

        val byteLength = trimmed.encodeToByteArray().size
        if (byteLength > 255) {
            return labels.socksLengthExceeded
        }

        if (!credentialsRegex.matches(trimmed)) {
            return labels.socksInvalidChars
        }

        return null
    }
}
