package com.antidoomscroller.core.blocklist

/** The verdict for a host, and which pattern produced it. */
data class MatchResult(val blocked: Boolean, val matchedPattern: String?)

/**
 * Suffix-trie matcher over domain names.
 *
 * Rules are stored by reversed label, so lookup costs one pass over the labels of the host being
 * checked - which matters because this runs on every DNS query the phone makes.
 *
 * Pattern forms:
 *  - `example.com`   blocks the apex and every subdomain
 *  - `*.example.com` blocks subdomains only, leaving the apex alone
 *  - `=example.com`  blocks exactly that name
 *
 * The most specific rule wins, so `block example.com` + `allow docs.example.com` does what it
 * looks like it does.
 */
class DomainMatcher private constructor(private val root: Node) {

    private class Node {
        val children = HashMap<String, Node>()
        var apex: Verdict? = null
        var descendants: Verdict? = null
        var apexPattern: String? = null
        var descendantPattern: String? = null
    }

    private enum class Verdict { BLOCK, ALLOW }

    fun match(host: String): MatchResult {
        val labels = normalise(host)?.split('.')?.reversed() ?: return MatchResult(false, null)

        var node: Node? = root
        var inherited: Verdict? = null
        var inheritedPattern: String? = null
        var lastIndex = -1

        for ((index, label) in labels.withIndex()) {
            val next = node?.children?.get(label) ?: break
            node = next
            lastIndex = index
            if (index < labels.lastIndex) {
                next.descendants?.let {
                    inherited = it
                    inheritedPattern = next.descendantPattern
                }
            }
        }

        // The full host matched a node: an exact rule there beats anything inherited.
        if (lastIndex == labels.lastIndex && node != null) {
            node.apex?.let { return MatchResult(it == Verdict.BLOCK, node.apexPattern) }
        }

        val verdict = inherited ?: return MatchResult(false, null)
        return MatchResult(verdict == Verdict.BLOCK, inheritedPattern)
    }

    fun isBlocked(host: String): Boolean = match(host).blocked

    class Builder {
        private val root = Node()
        private var ruleCount = 0

        fun block(pattern: String): Builder = add(pattern, Verdict.BLOCK)

        fun allow(pattern: String): Builder = add(pattern, Verdict.ALLOW)

        fun blockAll(patterns: Iterable<String>): Builder = apply { patterns.forEach { block(it) } }

        fun allowAll(patterns: Iterable<String>): Builder = apply { patterns.forEach { allow(it) } }

        fun size(): Int = ruleCount

        fun build(): DomainMatcher = DomainMatcher(root)

        private fun add(rawPattern: String, verdict: Verdict): Builder {
            val trimmed = rawPattern.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return this

            var subdomainsOnly = false
            var exactOnly = false
            var domain = trimmed

            when {
                domain.startsWith("*.") -> {
                    subdomainsOnly = true
                    domain = domain.removePrefix("*.")
                }

                domain.startsWith("=") -> {
                    exactOnly = true
                    domain = domain.removePrefix("=")
                }
            }

            val host = normalise(domain) ?: return this
            var node = root
            for (label in host.split('.').reversed()) {
                node = node.children.getOrPut(label) { Node() }
            }
            if (!subdomainsOnly) {
                node.apex = verdict
                node.apexPattern = trimmed
            }
            if (!exactOnly) {
                node.descendants = verdict
                node.descendantPattern = trimmed
            }
            ruleCount++
            return this
        }
    }

    companion object {
        val EMPTY: DomainMatcher = Builder().build()

        /** Lowercases, strips scheme/path/port/trailing dot, and rejects anything that is not a name. */
        fun normalise(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var value = raw.trim().lowercase()
            value = value.substringAfter("://")
            value = value.substringBefore('/')
            value = value.substringBefore('?')
            value = value.substringBefore('#')
            value = value.substringAfterLast('@')
            if (value.startsWith("[")) {
                // Bracketed IPv6 literal: never a domain rule.
                return null
            }
            value = value.substringBefore(':')
            value = value.trim('.')
            if (value.isEmpty()) return null
            if (!value.contains('.')) return null
            if (value.any { it.isWhitespace() }) return null
            if (!value.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' || it.code > 127 }) return null
            return value
        }
    }
}
