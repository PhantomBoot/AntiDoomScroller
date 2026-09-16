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

    /** Widget classes that mean "a video is playing here", whatever the app calls the view. */
    private val VIDEO_SURFACE_CLASSES = listOf("textureview", "surfaceview", "videoview")

    /** Share of the screen a video has to fill before it counts as a full-screen player. */
    private const val FULLSCREEN_PERCENT = 55

    /** Share of the width a video has to span before it counts as a post rather than a tile. */
    private const val FULL_WIDTH_PERCENT = 80

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

            val box = Rect().also(node::getBoundsInScreen).toScreenRect()

            // A snapshot describes what is on the screen, not what exists in the tree. Instagram
            // keeps the Reels fragment attached after you leave it, so its clips_* views are
            // still reachable from the DM inbox and the home feed - recording them regardless of
            // visibility meant the guard believed the reels player was open everywhere in the
            // app, and the cover never came down.
            val onScreen = node.isVisibleToUser && !box.intersect(screen).isEmpty
            if (!onScreen) {
                if (depth < MAX_DEPTH) {
                    for (index in 0 until node.childCount) {
                        val child = node.getChild(index) ?: continue
                        queue.addLast(child to depth + 1)
                    }
                }
                if (node !== root) recycle(node)
                continue
            }

            ScreenSnapshot.normaliseViewId(node.viewIdResourceName)?.let { id ->
                viewIds += id
                record(bounds, id, box, screen)

                // Which bottom-tab is selected says which part of the app the user is in, with no
                // guessing at container ids at all. The tab ids themselves - feed_tab, search_tab,
                // clips_tab - have been stable across every release seen so far, and unlike the
                // screens behind them they are always on screen to be read.
                if (node.isSelected) {
                    viewIds += "selected:$id"
                    record(bounds, "selected:$id", box, screen)
                }
            }

            node.className?.toString()?.lowercase()?.let { className ->
                classNames += className
                // A video is a video whatever its container is called this release, so the
                // playing surface is recorded under a synthetic id of its own. Signatures and
                // the region resolver can then ask for "class:textureview" exactly as they would
                // ask for any other id, which keeps the cover landing on the video even when the
                // app renames everything around it.
                VIDEO_SURFACE_CLASSES.firstOrNull { className.endsWith(it) }?.let { suffix ->
                    val key = "class:$suffix"
                    viewIds += key
                    record(bounds, key, box, screen)

                    // Geometry as evidence: a video filling the screen is a player, one sitting
                    // in a row is a post. Signatures can ask for either without naming a single
                    // container id, which is what stops them breaking every time Instagram
                    // renames its views.
                    val onScreenBox = box.intersect(screen)
                    val marker = if (onScreenBox.area * 100 >= screen.area * FULLSCREEN_PERCENT) {
                        "video:fullscreen"
                    } else {
                        "video:inline"
                    }
                    viewIds += marker
                    record(bounds, marker, box, screen)

                    // Width separates a post from a tile: a reel in a timeline runs the whole
                    // width of the screen, a thumbnail in a grid is a third of it. Without this
                    // an autoplaying tile on Explore read as a reel in the home feed.
                    val widthMarker = if (onScreenBox.width * 100 >= screen.width * FULL_WIDTH_PERCENT) {
                        "video:fullwidth"
                    } else {
                        "video:tile"
                    }
                    viewIds += widthMarker
                    record(bounds, widthMarker, box, screen)
                }
            }
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

    /** A list recycles ids across rows; keep whichever instance the user can actually see. */
    private fun record(
        bounds: MutableMap<String, ScreenRect>,
        key: String,
        box: ScreenRect,
        screen: ScreenRect,
    ) {
        if (box.isEmpty) return
        val existing = bounds[key]
        if (existing == null || box.intersect(screen).area > existing.intersect(screen).area) {
            bounds[key] = box
        }
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
