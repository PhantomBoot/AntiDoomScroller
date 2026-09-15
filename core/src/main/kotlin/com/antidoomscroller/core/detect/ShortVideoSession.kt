package com.antidoomscroller.core.detect

import com.antidoomscroller.core.model.FeedSurface

/**
 * One continuous visit to a short-video player, and where that visit started.
 *
 * Instagram's player looks identical whether the reel came from a message or from the Reels tab,
 * and the only evidence of the difference is the screen the user was on beforehand. Holding that
 * on a timer means a reel a friend sent is allowed for a few seconds and then blocked mid-watch,
 * on a stopwatch the user cannot see. The origin belongs to the *visit* instead: it holds until
 * the user leaves the player, however long they watch.
 *
 * Swiping onward is the one thing that genuinely changes what the screen is. The reel a friend
 * sent is one video; the next one is the feed choosing for you, which is the thing the app exists
 * to stop. [endDmAllowanceOnSwipe] decides whether that distinction is drawn.
 */
class ShortVideoSession {

    private var origin: FeedSurface? = null
    private var advances: Int = 0
    private var lastIndex: Int = INDEX_UNKNOWN

    val isActive: Boolean get() = origin != null
    val advanceCount: Int get() = advances

    /**
     * Maps the surface detected on screen to the one the rules should be applied to.
     *
     * @param endDmAllowanceOnSwipe when true, moving past the first video makes this the feed.
     */
    fun onClassified(detected: FeedSurface, endDmAllowanceOnSwipe: Boolean): FeedSurface {
        // A momentary failure to recognise the screen ends nothing; a video is still playing.
        if (detected == FeedSurface.UNKNOWN) return detected

        if (!detected.isShortVideo) {
            reset()
            return detected
        }

        val current = origin
        if (current == null) {
            origin = detected
            advances = 0
            lastIndex = INDEX_UNKNOWN
            return detected
        }

        // Only a player carries its origin. A unit embedded in a feed is judged where it sits, so
        // scrolling an ordinary timeline never turns it into the Reels feed.
        if (!current.isViewerOrigin()) return detected

        return if (endDmAllowanceOnSwipe && advances > 0) FeedSurface.SHORT_VIDEO_FEED else current
    }

    /**
     * Records a scroll inside the player. Returns true when it moved to a different video, which
     * is worth re-examining the screen for straight away.
     *
     * Only a change of item index counts. Without an index - a pager that does not report one -
     * nothing is counted, so the failure is towards leaving the user alone rather than blocking a
     * reel they were sent.
     */
    fun onScroll(fromIndex: Int): Boolean {
        if (origin == null || fromIndex < 0) return false
        if (lastIndex == INDEX_UNKNOWN) {
            lastIndex = fromIndex
            return false
        }
        if (fromIndex == lastIndex) return false
        lastIndex = fromIndex
        advances++
        return true
    }

    fun reset() {
        origin = null
        advances = 0
        lastIndex = INDEX_UNKNOWN
    }

    private fun FeedSurface.isViewerOrigin(): Boolean =
        this == FeedSurface.SHORT_VIDEO_IN_DM || this == FeedSurface.SHORT_VIDEO_IN_EXPLORE

    private companion object {
        const val INDEX_UNKNOWN = -1
    }
}
