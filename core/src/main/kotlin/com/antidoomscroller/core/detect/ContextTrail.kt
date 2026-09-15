package com.antidoomscroller.core.detect

/**
 * Remembers where the user just was.
 *
 * Instagram's reel player looks identical whether the reel came from a DM, from Explore, or from
 * the Reels tab, so "where did this open from" has to come from the screens immediately before it.
 * Tags expire, so a DM read ten minutes ago never unlocks the Reels tab.
 */
class ContextTrail(private val maxAgeMs: Long = 8_000L, private val capacity: Int = 16) {

    private data class Entry(val tag: String, val atMs: Long)

    private val entries = ArrayDeque<Entry>()
    private var lastPackage: String? = null

    fun record(tag: String, nowMs: Long) {
        entries.removeAll { it.tag == tag }
        entries.addLast(Entry(tag, nowMs))
        while (entries.size > capacity) entries.removeFirst()
    }

    fun recordAll(tags: Collection<String>, nowMs: Long) {
        tags.forEach { record(it, nowMs) }
    }

    /** Clears the trail when the user switches apps - context does not survive an app switch. */
    fun onPackageChanged(packageName: String) {
        if (packageName != lastPackage) {
            entries.clear()
            lastPackage = packageName
        }
    }

    fun activeTags(nowMs: Long): Set<String> {
        expire(nowMs)
        return entries.map { it.tag }.toSet()
    }

    fun ageOf(tag: String, nowMs: Long): Long? =
        entries.lastOrNull { it.tag == tag }?.let { nowMs - it.atMs }

    fun clear() = entries.clear()

    private fun expire(nowMs: Long) {
        while (entries.isNotEmpty() && nowMs - entries.first().atMs > maxAgeMs) {
            entries.removeFirst()
        }
    }
}
