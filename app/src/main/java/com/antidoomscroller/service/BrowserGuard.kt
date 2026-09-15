package com.antidoomscroller.service

import com.antidoomscroller.core.blocklist.DomainMatcher

/**
 * Second layer of the adult filter, for browsers that resolve names over HTTPS and so never ask
 * the phone's resolver at all.
 *
 * Reading the address bar catches those, plus anything typed as a bare IP. It only ever looks at
 * the URL field of a browser window, only while the filter is on, and the URL is checked and
 * discarded in the same breath.
 */
object BrowserGuard {

    val BROWSER_PACKAGES: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.opera.browser",
        "com.opera.browser.beta",
        "com.opera.mini.native",
        "com.opera.gx",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.UCMobile.intl",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.yandex.browser",
        "com.ecosia.android",
    )

    /** Address-bar view ids across the browsers above. */
    val URL_BAR_IDS: Set<String> = setOf(
        "url_bar",
        "url_field",
        "urlbar",
        "location_bar_edit_text",
        "mozac_browser_toolbar_url_view",
        "url_bar_title",
        "search_field",
        "address_bar_edit_text",
        "editor",
    )

    /** Returns the blocked host, or null when the address bar holds nothing worth acting on. */
    fun blockedHost(addressBarText: String?, matcher: DomainMatcher): String? {
        val host = DomainMatcher.normalise(addressBarText) ?: return null
        return if (matcher.isBlocked(host)) host else null
    }
}
