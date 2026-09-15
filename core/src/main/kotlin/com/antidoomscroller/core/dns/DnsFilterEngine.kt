package com.antidoomscroller.core.dns

import com.antidoomscroller.core.blocklist.DomainMatcher
import com.antidoomscroller.core.model.BlockResponseMode

/** What the tunnel should do with one DNS datagram. */
sealed interface DnsVerdict {
    /** Answer locally; nothing leaves the phone. */
    data class Blocked(val host: String, val response: ByteArray, val matchedPattern: String?) : DnsVerdict

    /** Send upstream unchanged. */
    data class Forward(val host: String?) : DnsVerdict

    /** Not something this filter understands; forward the raw bytes. */
    data object Passthrough : DnsVerdict
}

/** Counters for the app's own status screen. Kept in memory, never written anywhere. */
data class FilterStats(
    val queries: Long = 0,
    val blocked: Long = 0,
    val lastBlockedHost: String? = null,
    val lastBlockedAtMs: Long = 0,
)

/**
 * The decision half of the adult filter: query in, allow or refuse out.
 *
 * Deliberately free of Android and of sockets so the whole filter can be exercised in unit tests
 * with byte arrays.
 */
class DnsFilterEngine(
    @Volatile var matcher: DomainMatcher = DomainMatcher.EMPTY,
    @Volatile var responseMode: BlockResponseMode = BlockResponseMode.NXDOMAIN,
    @Volatile var enabled: Boolean = true,
) {

    @Volatile
    var stats: FilterStats = FilterStats()
        private set

    fun evaluate(payload: ByteArray, length: Int = payload.size, nowMs: Long = 0): DnsVerdict {
        if (!enabled) return DnsVerdict.Passthrough
        val query = DnsCodec.parseQuery(payload, length) ?: return DnsVerdict.Passthrough
        if (!query.isStandardQuery) return DnsVerdict.Forward(query.name)

        stats = stats.copy(queries = stats.queries + 1)

        val result = matcher.match(query.name)
        if (!result.blocked) return DnsVerdict.Forward(query.name)

        val request = if (length == payload.size) payload else payload.copyOf(length)
        val response = when (responseMode) {
            BlockResponseMode.NXDOMAIN -> DnsCodec.buildNxDomain(query, request)
            BlockResponseMode.NULL_IP -> DnsCodec.buildNullAddress(query, request)
        }
        stats = stats.copy(
            blocked = stats.blocked + 1,
            lastBlockedHost = query.name,
            lastBlockedAtMs = nowMs,
        )
        return DnsVerdict.Blocked(query.name, response, result.matchedPattern)
    }

    fun resetStats() {
        stats = FilterStats()
    }
}
