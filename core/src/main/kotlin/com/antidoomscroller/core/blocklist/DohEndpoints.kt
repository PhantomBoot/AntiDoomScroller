package com.antidoomscroller.core.blocklist

/**
 * DNS-over-HTTPS resolvers, blocked while the adult filter is on.
 *
 * A browser that resolves names over HTTPS never asks the phone's resolver, which would walk
 * straight past a DNS filter. Blocking the bootstrap names makes Chrome and Opera fall back to
 * the system resolver. It is not airtight on its own - a browser can ship a hardcoded IP - which
 * is exactly why the accessibility URL guard exists as a second layer.
 */
object DohEndpoints {

    val HOSTNAMES: List<String> = listOf(
        "dns.google",
        "dns64.dns.google",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "security.cloudflare-dns.com",
        "one.one.one.one",
        "dns.quad9.net",
        "dns10.quad9.net",
        "dns11.quad9.net",
        "doh.opendns.com",
        "doh.familyshield.opendns.com",
        "dns.nextdns.io",
        "doh.cleanbrowsing.org",
        "doh.dns.sb",
        "dns.adguard.com",
        "dns-family.adguard.com",
        "dns-unfiltered.adguard.com",
        "doh.libredns.gr",
        "dns.alidns.com",
        "doh.360.cn",
        "dns.twnic.tw",
        "odoh.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "dot.ffmuc.net",
        "dns.controld.com",
        "freedns.controld.com",
    )

    /** Names Chrome probes to decide whether the network resolver supports DoH upgrades. */
    val CANARY_HOSTNAMES: List<String> = listOf(
        "use-application-dns.net",
    )
}
