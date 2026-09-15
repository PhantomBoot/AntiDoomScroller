package com.antidoomscroller.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Works out which resolver to forward allowed queries to.
 *
 * Preference order: whatever the user typed in settings, then the resolvers the current network
 * handed the phone, then a public fallback so the phone is never left without DNS while the
 * filter is on. The app itself has no opinion about which resolver you use.
 */
object UpstreamResolvers {

    private val FALLBACK_V4 = listOf("1.1.1.1", "9.9.9.9")
    private val FALLBACK_V6 = listOf("2606:4700:4700::1111", "2620:fe::fe")

    data class Resolvers(val v4: List<InetAddress>, val v6: List<InetAddress>)

    fun discover(context: Context, configured: List<String>): Resolvers {
        val fromSettings = configured.mapNotNull { parse(it) }
        val discovered = if (fromSettings.isNotEmpty()) fromSettings else systemResolvers(context)
        val v4 = discovered.filterIsInstance<Inet4Address>().ifEmpty { FALLBACK_V4.mapNotNull { parse(it) } }
        val v6 = discovered.filterIsInstance<Inet6Address>().ifEmpty { FALLBACK_V6.mapNotNull { parse(it) } }
        return Resolvers(v4 = v4, v6 = v6)
    }

    /**
     * Reads the resolvers of the real network rather than of our own tunnel - once the VPN is up,
     * the "active" network is the VPN, and its DNS servers are the fake ones we installed.
     */
    private fun systemResolvers(context: Context): List<InetAddress> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val networks = manager.allNetworks
        for (network in networks) {
            val capabilities = manager.getNetworkCapabilities(network) ?: continue
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val servers = manager.getLinkProperties(network)?.dnsServers.orEmpty()
            if (servers.isNotEmpty()) return servers
        }
        return emptyList()
    }

    /** Parses a literal address only; a hostname here would need DNS to resolve DNS. */
    fun parse(value: String): InetAddress? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.all { it.isDigit() || it == '.' || it == ':' || it in "abcdefABCDEF" }) return null
        return runCatching { InetAddress.getByName(trimmed) }.getOrNull()
    }
}
