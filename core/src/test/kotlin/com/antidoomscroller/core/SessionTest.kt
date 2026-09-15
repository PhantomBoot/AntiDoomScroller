package com.antidoomscroller.core

import com.antidoomscroller.core.detect.ShortVideoSession
import com.antidoomscroller.core.model.FeedSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private fun session() = ShortVideoSession()

    @Test
    fun `a reel sent in a dm stays a dm reel for as long as it is watched`() {
        val session = session()
        assertEquals(
            FeedSurface.SHORT_VIDEO_IN_DM,
            session.onClassified(FeedSurface.SHORT_VIDEO_IN_DM, endDmAllowanceOnSwipe = true),
        )

        // The screen itself now reads as the plain Reels feed, because the navigation context
        // that proved it came from a message has aged out. That must not change the answer.
        repeat(50) {
            assertEquals(
                "watching longer must not turn a sent reel into the feed",
                FeedSurface.SHORT_VIDEO_IN_DM,
                session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
            )
        }
    }

    @Test
    fun `swiping to the next video makes it the feed`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_DM, true)
        assertFalse("the first index seen is the starting point", session.onScroll(4))
        assertTrue("moving to another item is an advance", session.onScroll(5))

        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
        assertEquals(1, session.advanceCount)
    }

    @Test
    fun `with the setting off the whole visit stays a dm reel`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_DM, false)
        session.onScroll(0)
        session.onScroll(1)
        session.onScroll(2)

        assertEquals(
            FeedSurface.SHORT_VIDEO_IN_DM,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = false),
        )
    }

    @Test
    fun `a pager that reports no index never promotes on its own`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_DM, true)
        repeat(20) { assertFalse(session.onScroll(-1)) }

        assertEquals(
            "without evidence of moving on, leave the user alone",
            FeedSurface.SHORT_VIDEO_IN_DM,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
    }

    @Test
    fun `leaving the player ends the visit`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_DM, true)
        session.onScroll(0)
        session.onScroll(1)

        assertEquals(
            FeedSurface.HOME_FEED,
            session.onClassified(FeedSurface.HOME_FEED, endDmAllowanceOnSwipe = true),
        )
        assertFalse(session.isActive)

        // Opening the Reels tab afterwards is the Reels tab, not a leftover dm reel.
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
    }

    @Test
    fun `an unrecognised frame does not end the visit`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_DM, true)
        assertEquals(
            FeedSurface.UNKNOWN,
            session.onClassified(FeedSurface.UNKNOWN, endDmAllowanceOnSwipe = true),
        )
        assertTrue(session.isActive)
        assertEquals(
            FeedSurface.SHORT_VIDEO_IN_DM,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
    }

    @Test
    fun `scrolling an ordinary timeline never turns it into the feed`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_HOME, true)
        session.onScroll(0)
        session.onScroll(1)
        session.onScroll(2)

        assertEquals(
            FeedSurface.SHORT_VIDEO_IN_HOME,
            session.onClassified(FeedSurface.SHORT_VIDEO_IN_HOME, endDmAllowanceOnSwipe = true),
        )
    }

    @Test
    fun `the reels tab is judged on its own from the first frame`() {
        val session = session()
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
        session.onScroll(0)
        assertTrue(session.onScroll(1))
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
    }

    @Test
    fun `a reel opened from explore behaves the same way`() {
        val session = session()
        session.onClassified(FeedSurface.SHORT_VIDEO_IN_EXPLORE, true)
        assertEquals(
            FeedSurface.SHORT_VIDEO_IN_EXPLORE,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
        session.onScroll(2)
        session.onScroll(3)
        assertEquals(
            FeedSurface.SHORT_VIDEO_FEED,
            session.onClassified(FeedSurface.SHORT_VIDEO_FEED, endDmAllowanceOnSwipe = true),
        )
    }
}
