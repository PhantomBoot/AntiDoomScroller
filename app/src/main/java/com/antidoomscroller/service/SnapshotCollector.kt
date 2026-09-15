package com.antidoomscroller.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.antidoomscroller.core.detect.ScreenRect
import com.antidoomscroller.core.detect.ScreenSnapshot

/**
 * Turns the live accessibility node tree into a flat [ScreenSnapshot].
 *
 * Bounded on purpose: a feed is an infinite list, and walking all of it on every content change
 * would cost more battery than the feed does. Breadth-first with hard caps means the top of the
 * hierarchy - which is where the identifying container ids live - is always seen.
 *
 * Nothing collected here is stored or logged. It is scored, used for one decision, and dropped.
 */
object SnapshotCollector {

    private const val MAX_NODES = 700
    private const val MAX_DEPTH = 24
    private const val MAX_TEXT_ENTRIES = 48
    private const val MAX_TEXT_LENGTH = 64

    fun collect(
        root: AccessibilityNodeInfo,
        packageName: String,
        nowMs: Long,
        screen: ScreenRect,
    ): ScreenSnapshot {
        val viewIds = HashSet<String>()
        val descriptions = HashSet<String>()
        val texts = HashSet<String>()
        val classNames = HashSet<String>()
        val bounds = LinkedHashMap<String, ScreenRect>()

        var visited = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            ScreenSnapshot.normaliseViewId(node.viewIdResourceName)?.let { id ->
                viewIds += id
                val box = Rect().also(node::getBoundsInScreen).toScreenRect()
                if (!box.isEmpty) {
                    val existing = bounds[id]
                    // A list recycles the same id for rows above and below the viewport; keep the
                    // one the user can actually see.
                    if (existing == null ||
                        box.intersect(screen).area > existing.intersect(screen).area
                    ) {
                        bounds[id] = box
                    }
                }
            }
            node.className?.toString()?.lowercase()?.let(classNames::add)
            if (descriptions.size < MAX_TEXT_ENTRIES) {
                node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    descriptions += it.take(MAX_TEXT_LENGTH).lowercase()
                }
            }
            if (texts.size < MAX_TEXT_ENTRIES) {
                node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    texts += it.take(MAX_TEXT_LENGTH).lowercase()
                }
            }

            if (depth < MAX_DEPTH) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.addLast(child to depth + 1)
                }
            }
            if (node !== root) recycle(node)
        }

        // Anything still queued when a cap was hit still has to be released on older releases.
        drain(queue.map { it.first }, root)

        return ScreenSnapshot(
            packageName = packageName,
            viewIds = viewIds,
            contentDescriptions = descriptions,
            texts = texts,
            classNames = classNames,
            timestampMs = nowMs,
            bounds = bounds,
        )
    }

    /** Finds the address-bar text in a browser window, or null when it is not on screen. */
    fun findUrlBarText(root: AccessibilityNodeInfo): String? {
        var visited = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var found: String? = null

        while (queue.isNotEmpty() && visited < MAX_NODES && found == null) {
            val node = queue.removeFirst()
            visited++
            val id = ScreenSnapshot.normaliseViewId(node.viewIdResourceName)
            if (id != null && BrowserGuard.URL_BAR_IDS.any { id == it || id.endsWith(it) }) {
                found = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }
            if (found == null) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.addLast(child)
                }
            }
            if (node !== root) recycle(node)
        }

        drain(queue.toList(), root)
        return found
    }

    private fun Rect.toScreenRect(): ScreenRect = ScreenRect(left, top, right, bottom)

    private fun drain(remaining: List<AccessibilityNodeInfo>, root: AccessibilityNodeInfo) {
        remaining.forEach { if (it !== root) recycle(it) }
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { node.recycle() }
        }
    }
}
