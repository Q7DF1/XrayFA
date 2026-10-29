package com.android.xrayfa.ui.home

import com.android.xrayfa.common.config.DnsDefaults
import com.android.xrayfa.common.core.AssetFiles
import com.android.xrayfa.common.core.NodeDnsResolver
import com.android.xrayfa.common.core.MmdbCityLookup
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.parseIpLiteral
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.network.SocksProxyConfig
import com.android.xrayfa.network.exchangeDnsThroughSocks
import com.android.xrayfa.shared.ui.home.HomeMapIssue
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.net.IDN
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Uses configured DoH or ordinary DNS through local SOCKS; coordinates remain in City MMDB. */
class AndroidHomeIpLocationLookup(
    private val assetPaths: XrayAssetPaths,
    private val settingsRepository: SettingsRepository,
    proxyClient: HttpClient,
) {
    private val client = proxyClient.config { followRedirects = false }
    private val resolver = NodeDnsResolver(
        exchange = { mode, endpoint, query ->
            if (mode == NodeDnsResolver.Mode.PLAIN) {
                val settings = settingsRepository.settingsFlow.first()
                NodeDnsResolver.Response(exchangeDnsThroughSocks(
                    endpoint, query, SocksProxyConfig(settings.socksPort, settings.socksUserName, settings.socksPassword),
                ))
            } else {
                val response = client.post(endpoint) {
                    contentType(ContentType.parse("application/dns-message"))
                    headers[HttpHeaders.Accept] = "application/dns-message"
                    setBody(query)
                }
                check(response.status.isSuccess()) { "DoH HTTP request failed" }
                check(response.contentType()?.withoutParameters()?.toString() == "application/dns-message")
                NodeDnsResolver.Response(response.body<ByteArray>(), response.headers[HttpHeaders.Age]?.toLongOrNull() ?: 0)
            }
        },
        now = System::currentTimeMillis,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )
    private var databaseVersion: Pair<Long, Long>? = null
    private var reader: MmdbCityLookup? = null

    suspend fun lookup(
        nodeAddress: String?,
        sessionId: Long?,
        socksInboundAvailable: Boolean,
    ): HomeMapLocations = withContext(Dispatchers.IO) {
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
        val language = Locale.getDefault().toLanguageTag()
        val ips = if (parseIpLiteral(address) != null) listOf(address) else {
            if (sessionId == null || !socksInboundAvailable) {
                return@withContext HomeMapLocations(issue = HomeMapIssue.DNS_UNAVAILABLE)
            }
            val host = runCatching { IDN.toASCII(address.trimEnd('.')) }.getOrNull()
                ?: return@withContext HomeMapLocations(issue = HomeMapIssue.DNS_UNAVAILABLE)
            val settings = settingsRepository.settingsFlow.first()
            val mode = if (settings.dnsOverHttpsEnabled) NodeDnsResolver.Mode.DOH else NodeDnsResolver.Mode.PLAIN
            val upstreams = if (settings.dnsOverHttpsEnabled) DnsDefaults.parseDohServers(settings.dnsOverHttpsServers)
                else DnsDefaults.parseUdpDnsServers(settings.udpDnsServers)
            resolver.resolve(host, upstreams, settings.ipV6Enable, sessionId, mode)
                .takeIf { it.isNotEmpty() }
                ?: return@withContext HomeMapLocations(issue = HomeMapIssue.DNS_UNAVAILABLE)
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
}
