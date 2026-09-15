package com.antidoomscroller.service

import com.antidoomscroller.core.detect.Classification
import com.antidoomscroller.core.detect.ScreenSnapshot

/**
 * A short, opt-in record of what the guard saw, for when a feed stops being recognised.
 *
 * Instagram renames its views between releases, and every rename is otherwise a guessing game
 * played through the person using the app. This turns it into data they can read and send.
 *
 * Off by default, held in memory only, capped at a handful of entries, and dropped when the
 * service stops. Nothing here is ever written to disk or leaves the device unless the person
 * copies it out themselves.
 */
object DetectionLog {

    private const val CAPACITY = 8
    private const val MAX_IDS = 60

    data class Entry(
        val packageName: String,
        val surface: String,
        val evidence: List<String>,
        val viewIds: List<String>,
        val descriptions: List<String>,
    )

    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            if (!value) clear()
        }

    private val entries = ArrayDeque<Entry>()

    fun record(snapshot: ScreenSnapshot, classification: Classification) {
        if (!enabled) return
        val entry = Entry(
            packageName = snapshot.packageName,
            surface = classification.surface.id,
            evidence = classification.evidence,
            viewIds = snapshot.viewIds.sorted().take(MAX_IDS),
            descriptions = snapshot.contentDescriptions.sorted().take(MAX_IDS),
        )
        synchronized(entries) {
            // Repeated frames of one screen say nothing new; keep the distinct ones.
            if (entries.firstOrNull()?.viewIds == entry.viewIds) return
            entries.addFirst(entry)
            while (entries.size > CAPACITY) entries.removeLast()
        }
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    /** The report as plain text, for pasting into a bug report. */
    fun asText(): String {
        val captured = snapshot()
        if (captured.isEmpty()) return "Nothing recorded yet."
        return captured.joinToString("\n\n") { entry ->
            buildString {
                append("app: ").append(entry.packageName).append('\n')
                append("detected: ").append(entry.surface).append('\n')
                if (entry.evidence.isNotEmpty()) {
                    append("matched: ").append(entry.evidence.joinToString(", ")).append('\n')
                }
                append("view ids: ").append(entry.viewIds.joinToString(", ")).append('\n')
                append("descriptions: ").append(entry.descriptions.joinToString(", "))
            }
        }
    }
}
