package com.antidoomscroller.core.blocklist

import com.antidoomscroller.core.model.AdultFilterSettings

/** A built matcher plus how many rules went into it, for the status screen. */
data class Ruleset(
    val matcher: DomainMatcher,
    val bundledCount: Int,
    val customCount: Int,
    val allowlistCount: Int,
) {
    val totalBlocked: Int get() = bundledCount + customCount
}

/**
 * Assembles the live ruleset from the bundled list, the user's own additions, and their
 * allowlist.
 *
 * Order matters: everything is loaded into one trie where the most specific rule wins, so a user
 * allowlist entry beats a broader block from the bundled list without having to edit it.
 */
object AdultFilterRuleset {

    fun build(settings: AdultFilterSettings, bundledDomains: List<String>): Ruleset {
        val builder = DomainMatcher.Builder()

        var bundled = 0
        if (settings.useBundledList) {
            bundledDomains.forEach { domain ->
                if (DomainMatcher.normalise(domain) != null) {
                    builder.block(domain)
                    bundled++
                }
            }
        }

        if (settings.blockDnsOverHttpsBypass) {
            DohEndpoints.HOSTNAMES.forEach { builder.block(it) }
            DohEndpoints.CANARY_HOSTNAMES.forEach { builder.block(it) }
        }

        var custom = 0
        settings.customBlockedDomains.forEach { domain ->
            if (DomainMatcher.normalise(domain) != null) {
                builder.block(domain)
                custom++
            }
        }

        var allowed = 0
        settings.allowlistDomains.forEach { domain ->
            if (DomainMatcher.normalise(domain) != null) {
                builder.allow(domain)
                allowed++
            }
        }

        return Ruleset(
            matcher = builder.build(),
            bundledCount = bundled,
            customCount = custom,
            allowlistCount = allowed,
        )
    }
}
