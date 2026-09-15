package com.antidoomscroller.core.detect

/**
 * A flattened, framework-free description of what is on screen right now.
 *
 * The Android accessibility service walks the node tree and fills this in; everything downstream
 * is plain Kotlin, which is what makes the detection logic testable without a device.
 *
 * @param viewIds resource id names with the `pkg:id/` prefix already stripped, lowercased.
 * @param contentDescriptions lowercased content descriptions.
 * @param texts lowercased visible text, capped by the collector.
 * @param classNames lowercased view class names seen in the tree.
 */
data class ScreenSnapshot(
    val packageName: String,
    val viewIds: Set<String> = emptySet(),
    val contentDescriptions: Set<String> = emptySet(),
    val texts: Set<String> = emptySet(),
    val classNames: Set<String> = emptySet(),
    val timestampMs: Long = 0L,
) {
    fun hasViewIdContaining(fragment: String): Boolean = viewIds.any { it.contains(fragment) }

    fun hasDescriptionContaining(fragment: String): Boolean =
        contentDescriptions.any { it.contains(fragment) }

    fun hasTextContaining(fragment: String): Boolean = texts.any { it.contains(fragment) }

    companion object {
        /** Normalises `com.instagram.android:id/clips_viewer_root` to `clips_viewer_root`. */
        fun normaliseViewId(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val idx = raw.indexOf("/")
            val tail = if (idx >= 0) raw.substring(idx + 1) else raw
            return tail.trim().lowercase().ifBlank { null }
        }
    }
}
