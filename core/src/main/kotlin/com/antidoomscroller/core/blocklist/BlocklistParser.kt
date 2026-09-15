package com.antidoomscroller.core.blocklist

/**
 * Reads the list formats people actually have lying around: hosts files, plain domain lists, and
 * adblock-style `||domain^` rules. Imports are local files the user picks - the app never fetches
 * a list from the network.
 */
object BlocklistParser {

    private val IGNORED_HOSTS = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback")
    private val NULL_TARGETS = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "0000:0000:0000:0000:0000:0000:0000:0000")

    data class ParseResult(
        val domains: List<String>,
        val skippedLines: Int,
    )

    fun parse(text: String): ParseResult = parseLines(text.lineSequence())

    fun parseLines(lines: Sequence<String>): ParseResult {
        val domains = LinkedHashSet<String>()
        var skipped = 0
        for (rawLine in lines) {
            val line = stripComment(rawLine)
            if (line.isBlank()) continue
            val parsed = parseLine(line)
            if (parsed == null) {
                skipped++
                continue
            }
            domains += parsed
        }
        return ParseResult(domains.toList(), skipped)
    }

    private fun stripComment(line: String): String {
        val withoutHash = line.substringBefore('#')
        val withoutBang = withoutHash.substringBefore('!')
        return withoutBang.trim()
    }

    /** Returns the domain a line refers to, or null when the line is not a usable rule. */
    fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Adblock syntax: ||example.com^
        if (trimmed.startsWith("||")) {
            val domain = trimmed.removePrefix("||").substringBefore('^').substringBefore('$')
            return DomainMatcher.normalise(domain)
        }
        // Adblock exception and element rules are not domain rules.
        if (trimmed.startsWith("@@") || trimmed.contains("##")) return null

        val parts = trimmed.split(Regex("\\s+"))
        return when {
            // hosts file: 0.0.0.0 example.com
            parts.size >= 2 && parts[0] in NULL_TARGETS -> {
                parts.drop(1).firstNotNullOfOrNull { candidate ->
                    DomainMatcher.normalise(candidate)?.takeUnless { it in IGNORED_HOSTS }
                }
            }
            // hosts file pointing somewhere else entirely: ignore, it is not a block rule.
            parts.size >= 2 -> null
            else -> DomainMatcher.normalise(parts[0])?.takeUnless { it in IGNORED_HOSTS }
        }
    }
}
