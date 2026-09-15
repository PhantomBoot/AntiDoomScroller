package com.antidoomscroller.core.scroll

import com.antidoomscroller.core.model.FeedTimeSettings

/**
 * How long the user has actually been scrolling, rather than how long the app has been open.
 *
 * Only the gaps between consecutive scroll events count, and only short ones: a pause to read a
 * post, or to answer a message, is not scrolling. So the budget measures the thing the limit is
 * about - continuous feed movement - and a five minute limit means five minutes of it.
 */
class ScrollTimeTracker {

    private var lastScrollMs: Long = NEVER
    private var spent: Long = 0

    val spentMs: Long get() = spent

    /** Records a scroll and returns the total time spent scrolling so far. */
    fun onScroll(nowMs: Long, settings: FeedTimeSettings): Long {
        if (lastScrollMs != NEVER) {
            val gap = nowMs - lastScrollMs
            if (gap in 1..settings.idleGapMs) spent += gap
        }
        lastScrollMs = nowMs
        return spent
    }

    /**
     * Called while the user is somewhere other than the feed. The budget only refills after a
     * real break, so stepping into a message thread and straight back does not reset it.
     */
    fun onAwayFromFeed(nowMs: Long, settings: FeedTimeSettings) {
        if (lastScrollMs == NEVER) return
        if (nowMs - lastScrollMs >= settings.resetAfterAwayMs) reset()
    }

    fun isOverLimit(settings: FeedTimeSettings): Boolean =
        settings.enabled && spent >= settings.limitMs

    fun remainingMs(settings: FeedTimeSettings): Long = (settings.limitMs - spent).coerceAtLeast(0)

    fun reset() {
        spent = 0
        lastScrollMs = NEVER
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
    }
}
