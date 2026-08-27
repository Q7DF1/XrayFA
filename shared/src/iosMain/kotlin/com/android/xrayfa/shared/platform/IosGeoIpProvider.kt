@file:OptIn(ExperimentalForeignApi::class)

package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.core.GeoIpProvider
import com.android.xrayfa.common.core.MmdbCountryLookup
import com.android.xrayfa.common.core.XrayAssetPaths
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.in_addr_t
import platform.posix.sockaddr_in
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.SEEK_END
import platform.posix.SEEK_SET

/** GeoLite2-Country.mmdb reader for node country flags (same file as Android). */
class IosGeoIpProvider(
    private val assetPaths: XrayAssetPaths,
) : GeoIpProvider {
    override fun countryIsoFromIp(ip: String): String {
        return try {
            val lookupIp = ipForLookup(ip) ?: return ""
            val bytes = readFileBytes(assetPaths.geoLiteDatabasePath) ?: return ""
            MmdbCountryLookup.countryFlag(bytes, lookupIp)
        } catch (_: Exception) {
            ""
        }
    }
}

private fun ipForLookup(ip: String): String? {
    val trimmed = ip.trim()
    if (trimmed.isEmpty()) return null
    if (MmdbCountryLookup.isIpLiteral(trimmed)) return trimmed
    return resolveHostname(trimmed)
}

private fun readFileBytes(path: String): ByteArray? {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val file = fopen(path, "rb") ?: return null
    try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file)
        if (size <= 0L) return null
        fseek(file, 0, SEEK_SET)
        val buf = ByteArray(size.toInt())
        val read =
            buf.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, size.toULong(), file).toLong()
            }
        return if (read == size) buf else null
    } finally {
        fclose(file)
    }
}

private fun resolveHostname(host: String): String? = memScoped {
    val hints = alloc<addrinfo>()
    hints.ai_family = AF_INET
    hints.ai_socktype = SOCK_STREAM
    val result = alloc<CPointerVar<addrinfo>>()
    if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) return@memScoped null
    val head = result.value ?: return@memScoped null
    try {
        var info: CPointer<addrinfo>? = head
        while (info != null) {
            val ai = info.pointed
            val addr = ai.ai_addr
            if (ai.ai_family == AF_INET && addr != null) {
                return@memScoped ipv4String(addr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr)
            }
            info = ai.ai_next
        }
        null
    } finally {
        freeaddrinfo(head)
    }
}

/** Darwin `s_addr` is network-order; on LE this yields a.b.c.d from the low byte first. */
private fun ipv4String(sAddr: in_addr_t): String {
    val v = sAddr.toInt()
    return "${v and 0xFF}.${(v ushr 8) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 24) and 0xFF}"
}

