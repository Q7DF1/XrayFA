package com.android.xrayfa.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.android.xrayfa.common.core.AssetFiles
import com.android.xrayfa.common.core.MmdbCityLookup
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.parseIpLiteral
import com.android.xrayfa.shared.ui.home.HomeMapIssue
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Resolves only the node on the physical network; coordinates come from the local City MMDB. */
class AndroidHomeIpLocationLookup(
    context: Context,
    private val assetPaths: XrayAssetPaths,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var databaseVersion: Pair<Long, Long>? = null
    private var reader: MmdbCityLookup? = null

    suspend fun lookup(nodeAddress: String?): HomeMapLocations = withContext(Dispatchers.IO) {
        val address = nodeAddress?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withContext HomeMapLocations()
        val file = File(assetPaths.cityDatabasePath)
        if (!file.isFile) {
            reader = null
            databaseVersion = null
            return@withContext HomeMapLocations(issue = HomeMapIssue.DATABASE_MISSING)
        }
        val db = try {
            val version = file.lastModified() to file.length()
            if (reader == null || databaseVersion != version) {
                reader = null
                val bytes = AssetFiles.read(file.path) ?: error("Missing database")
                reader = MmdbCityLookup(bytes)
                databaseVersion = version
            }
            requireNotNull(reader)
        } catch (_: Exception) {
            return@withContext HomeMapLocations(issue = HomeMapIssue.DATABASE_INVALID)
        }
        val network = findDirectInternetNetwork()
        val language = Locale.getDefault().toLanguageTag()
        val ips = if (parseIpLiteral(address) != null) listOf(address) else {
            runCatching { network?.getAllByName(address)?.mapNotNull { it.hostAddress } }
                .getOrNull().orEmpty()
        }
        val server = ips.firstNotNullOfOrNull { locate(db, it, language) }
        HomeMapLocations(
            server = server,
            issue = if (server == null) HomeMapIssue.SERVER_UNAVAILABLE else null,
        )
    }

    private fun locate(db: MmdbCityLookup, ip: String, language: String): HomeMapLocation? =
        runCatching {
            db.location(ip, language)?.let {
                HomeMapLocation(ip, it.city, it.region, it.country, it.latitude, it.longitude)
            }
        }.getOrNull()

    private fun findDirectInternetNetwork(): Network? {
        val candidates = connectivityManager.allNetworks.filter { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        return candidates.firstOrNull { it == connectivityManager.activeNetwork }
            ?: candidates.firstOrNull { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            } ?: candidates.firstOrNull()
    }
}
