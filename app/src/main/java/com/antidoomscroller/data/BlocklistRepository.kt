package com.antidoomscroller.data

import android.content.Context
import com.antidoomscroller.core.blocklist.AdultFilterRuleset
import com.antidoomscroller.core.blocklist.BlocklistParser
import com.antidoomscroller.core.blocklist.DomainMatcher
import com.antidoomscroller.core.blocklist.Ruleset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Builds the live adult-filter ruleset from the bundled seed list plus whatever the user added.
 *
 * The bundled list is an asset inside the APK and the import path reads a file the user picks.
 * Nothing here ever downloads a list - a filter that phones out for its rules is a filter that
 * tells someone else what you tried to visit.
 */
class BlocklistRepository(
    private val context: Context,
    scope: CoroutineScope,
    settingsRepository: SettingsRepository,
) {

    private val bundledDomains: List<String> by lazy { loadBundled() }

    val ruleset: StateFlow<Ruleset> = settingsRepository.settings
        .map { settings -> AdultFilterRuleset.build(settings.adultFilter, bundledDomains) }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            Ruleset(DomainMatcher.EMPTY, 0, 0, 0),
        )

    fun bundledCount(): Int = bundledDomains.size

    /** Parses a list the user chose from their own storage. Returns the domains found. */
    fun parseImport(text: String): BlocklistParser.ParseResult = BlocklistParser.parse(text)

    private fun loadBundled(): List<String> = runCatching {
        context.assets.open(BUNDLED_ASSET).bufferedReader().use { reader ->
            BlocklistParser.parseLines(reader.lineSequence()).domains
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val BUNDLED_ASSET = "blocklists/adult_domains.txt"
    }
}
