package com.android.xrayfa.model

import com.android.xrayfa.common.utils.UrlCodec
import com.android.xrayfa.common.utils.ShareLinkCleaner
import com.android.xrayfa.config.XDriveForm
import com.android.xrayfa.config.XDriveShareLink
import com.android.xrayfa.config.JsonConfigError
import com.android.xrayfa.config.JsonConfigException

const val JSON_CONFIG_PREFIX = "json-config"
val Node.isJsonConfig: Boolean get() = protocolPrefix == JSON_CONFIG_PREFIX
/** An app-created VLESS node backed by local JSON; global JSON imports stay generic. */
val Node.isManagedXDrive: Boolean get() = isJsonConfig && url.substringAfter("://").substringBefore('?').startsWith("xdrive-")
/** All export paths use this allowlisted projection rather than a local JSON identifier. */
fun Node.configurationShareLink(): String? = when {
    isManagedXDrive -> jsonData?.let { XDriveShareLink.fromJson(it, remark) }
    !isJsonConfig && jsonData == null -> ShareLinkCleaner.cleanUrlForSharing(url)
    else -> null
}
val Node.canShareConfiguration: Boolean get() = configurationShareLink() != null
val Node.requiresXDriveSetup: Boolean
    get() {
        if (!isManagedXDrive) return false
        val form = XDriveForm.fromJson(jsonData.orEmpty())
        return form == null || form.issue() != null
    }
fun Node.requireXDriveReady() {
    if (requiresXDriveSetup) throw JsonConfigException(JsonConfigError.XDRIVE_SETUP_REQUIRED)
}
val Node.jsonInboundTag: String?
    get() = if (isJsonConfig) url.substringAfter("?inbound=", "").takeIf { it.isNotEmpty() }?.let(UrlCodec::decode) else null

fun jsonConfigIdentifier(id: String, inboundTag: String?): String =
    "$JSON_CONFIG_PREFIX://$id" + (inboundTag?.takeIf { it.isNotBlank() }?.let { "?inbound=${UrlCodec.encode(it)}" } ?: "")
