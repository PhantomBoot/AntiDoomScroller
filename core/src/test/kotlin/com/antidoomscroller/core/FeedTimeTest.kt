package com.antidoomscroller.core

import com.antidoomscroller.core.detect.DefaultSignatures
import com.antidoomscroller.core.detect.ScreenSnapshot
import com.antidoomscroller.core.detect.SurfaceClassifier
import com.antidoomscroller.core.model.ContextTag
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.FeedTimeSettings
import com.antidoomscroller.core.model.SupportedApps
import com.antidoomscroller.core.scroll.ScrollTimeTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTimeTest {

    private val second = 1_000L
    private val minute = 60 * second
    private val settings = FeedTimeSettings(limitMinutes = 5, idleGapSeconds = 10, resetAfterAwayMinutes = 5)
    private val tracker = ScrollTimeTracker()

    /** Scrolls every [everyMs] for [count] events, as a thumb actually moves. */
    private fun scroll(startMs: Long, count: Int, everyMs: Long = second): Long {
        var at = startMs
        repeat(count) {
            tracker.onScroll(at, settings)
            at += everyMs
        }
        return at
    }

    @Test
    fun `five minutes of scrolling reaches the limit`() {
        assertFalse(tracker.isOverLimit(settings))
        scroll(startMs = 0, count = 301)
        assertTrue(tracker.isOverLimit(settings))
        assertEquals(0, tracker.remainingMs(settings))
    }

    @Test
    fun `time spent reading a post is not time spent scrolling`() {
        tracker.onScroll(0, settings)
        // Half an hour on one post, then another flick. Only the flick counts.
        tracker.onScroll(30 * minute, settings)
        tracker.onScroll(30 * minute + second, settings)
        assertEquals(second, tracker.spentMs)
        assertFalse(tracker.isOverLimit(settings))
    }

    @Test
    fun `stepping into a message thread and back does not refill the budget`() {
        val after = scroll(startMs = 0, count = 200)
        val spent = tracker.spentMs

        tracker.onAwayFromFeed(after + 30 * second, settings)
        assertEquals(spent, tracker.spentMs)

        // A real break does refill it.
        tracker.onAwayFromFeed(after + 6 * minute, settings)
        assertEquals(0, tracker.spentMs)
    }

    @Test
    fun `the budget picks up where it left off after a short detour`() {
        val after = scroll(startMs = 0, count = 200)
        tracker.onAwayFromFeed(after + 30 * second, settings)
        scroll(startMs = after + 60 * second, count = 200)
        assertTrue(tracker.spentMs > 350 * second)
    }

    @Test
    fun `a disabled limit never fires`() {
        val off = settings.copy(enabled = false)
        scroll(startMs = 0, count = 600)
        assertFalse(tracker.isOverLimit(off))
    }

    @Test
    fun `the remaining time counts down`() {
        scroll(startMs = 0, count = 61)
        assertEquals(4 * minute, tracker.remainingMs(settings))
    }

    @Test
    fun `a reel opened from explore is caught by a video filling the screen`() {
        // The container ids were guesses; a video that fills the screen, reached from the explore
        // grid, is the evidence that survives a rename.
        val classifier = SurfaceClassifier(DefaultSignatures.pack())
        val player = ScreenSnapshot(
            SupportedApps.INSTAGRAM,
            viewIds = setOf("class:textureview", "video:fullscreen"),
            contentDescriptions = setOf("reel by alice", "like", "comment"),
        )
        assertEquals(
            FeedSurface.SHORT_VIDEO_IN_EXPLORE,
            classifier.classify(player, setOf(ContextTag.EXPLORE)).surface,
        )
    }

    @Test
    fun `an inline video in the timeline is not a full-screen player`() {
        val classifier = SurfaceClassifier(DefaultSignatures.pack())
        val feed = ScreenSnapshot(
            SupportedApps.INSTAGRAM,
            viewIds = setOf(
                "feed_recycler_view",
                "class:textureview",
                "video:inline",
                "video:fullwidth",
                "tab_bar",
            ),
            contentDescriptions = setOf("reel by alice"),
        )
        assertEquals(FeedSurface.SHORT_VIDEO_IN_HOME, classifier.classify(feed, emptySet()).surface)
    }
}
