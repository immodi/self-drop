package com.projects.selfdrop.android.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference

sealed interface DiscoveryResult {
    data class Found(
        val baseUrl: String,
        val serverId: String,
        val source: String,
    ) : DiscoveryResult

    data class DifferentServer(
        val baseUrl: String,
        val serverId: String,
        val previousId: String?,
    ) : DiscoveryResult

    data class NotFound(val detail: String) : DiscoveryResult
}

/**
 * Finds the server in the order described by spec.md section 9.4:
 * cached address, rendezvous Worker, then a /24 probe whose `/ping` id must match
 * the stored id. QR and manual entry are handled by the UI instead.
 */
class Discovery(
    private val api: SelfdropApi,
    private val context: Context,
) {

    suspend fun discover(
        cachedUrl: String?,
        knownId: String?,
        workerUrl: String,
    ): DiscoveryResult {
        if (!cachedUrl.isNullOrBlank()) {
            val id = runCatching { api.ping(cachedUrl, timeoutMillis = 1200) }.getOrNull()
            if (id != null) {
                if (knownId == null || id == knownId) {
                    return DiscoveryResult.Found(cachedUrl, id, "saved address")
                }
                return DiscoveryResult.DifferentServer(cachedUrl, id, knownId)
            }
        }

        if (workerUrl.isNotBlank()) {
            val record = runCatching { api.workerRecord(workerUrl) }.getOrNull()
            if (record != null) {
                val id = runCatching { api.ping(record.url, timeoutMillis = 2500) }.getOrNull()
                if (id != null) {
                    if (knownId == null || id == knownId) {
                        return DiscoveryResult.Found(record.url, id, "rendezvous Worker")
                    }
                    return DiscoveryResult.DifferentServer(record.url, id, knownId)
                }
            }
        }

        // The scan can only confirm a *known* identity. Without one, a random
        // Selfdrop on the network would be accepted silently.
        if (knownId != null) {
            val found = subnetScan(knownId)
            if (found != null) {
                return DiscoveryResult.Found(found, knownId, "LAN scan")
            }
        }

        return DiscoveryResult.NotFound(
            "Could not find your server. Make sure Selfdrop is running on this network, " +
                "then try again or scan its pairing QR code.",
        )
    }

    private suspend fun subnetScan(knownId: String): String? = coroutineScope {
        val localIp = localIpv4Address() ?: return@coroutineScope null
        val prefix = localIp.split('.').take(3).joinToString(".")
        val found = AtomicReference<String?>(null)
        val permits = Semaphore(48)

        (1..254).map { host ->
            async(Dispatchers.IO) {
                if (found.get() != null) return@async
                permits.withPermit {
                    if (found.get() != null) return@withPermit
                    val url = "http://$prefix.$host:8080"
                    val id = runCatching { api.ping(url, timeoutMillis = 400) }.getOrNull()
                    if (id == knownId) found.compareAndSet(null, url)
                }
            }
        }.awaitAll()

        found.get()
    }

    private fun localIpv4Address(): String? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        val onLan = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!onLan) return null

        val properties = manager.getLinkProperties(network) ?: return null
        return properties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }
}

/** Parsing for `selfdrop://pair` links, QR contents and manually entered addresses. */
object Pairing {
    data class Target(val baseUrl: String, val serverId: String?)

    fun parse(raw: String): Target? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        if (!value.startsWith("selfdrop://pair", ignoreCase = true)) {
            return normalizeBaseUrl(value)?.let { Target(it, null) }
        }

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("selfdrop", ignoreCase = true) ||
            !uri.host.equals("pair", ignoreCase = true)
        ) {
            return null
        }
        val parameters = parseQuery(uri.rawQuery)
        val url = parameters["u"] ?: return null
        val id = parameters["id"]?.takeIf { it.isNotBlank() }
        return normalizeBaseUrl(url)?.let { Target(it, id) }
    }

    fun normalizeBaseUrl(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        if (!value.contains("://")) value = "http://$value"

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("http", ignoreCase = true)) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port == -1) 8080 else uri.port
        if (port !in 1..65535) return null
        return "http://$host:$port"
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 0) return@mapNotNull null
            val key = URLDecoder.decode(part.substring(0, separator), "UTF-8")
            val value = URLDecoder.decode(part.substring(separator + 1), "UTF-8")
            key to value
        }.toMap()
    }
}
