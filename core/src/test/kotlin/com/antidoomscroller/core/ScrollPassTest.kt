package com.antidoomscroller.core

import com.antidoomscroller.core.detect.Classification
import com.antidoomscroller.core.lock.ScrollPassController
import com.antidoomscroller.core.lock.ScrollPassPhase
import com.antidoomscroller.core.lock.ScrollPassState
import com.antidoomscroller.core.lock.TimeReading
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.GuardSettings
import com.antidoomscroller.core.model.ScrollPassSettings
import com.antidoomscroller.core.model.SupportedApps
import com.antidoomscroller.core.policy.AllowReason
import com.antidoomscroller.core.policy.GuardDecision
import com.antidoomscroller.core.policy.PolicyResolver
import com.antidoomscroller.core.util.Durations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScrollPassTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val settings = ScrollPassSettings(durationMinutes = 15, cooldownHours = 24)

    /** Wall clock and uptime moving together, as they do when nobody is interfering. */
    private fun at(millis: Long) = TimeReading(millis, millis)

    private fun started(): ScrollPassState =
        ScrollPassController.start(ScrollPassState(), at(0), settings)

    @Test
    fun `the button is there until it is pressed`() {
        assertEquals(ScrollPassPhase.AVAILABLE, ScrollPassController.phase(ScrollPassState(), settings))
        assertEquals(ScrollPassPhase.RUNNING, ScrollPassController.phase(started(), settings, at(0)))
    }

    @Test
    fun `fifteen minutes of scrolling, then blocking is back`() {
        val state = started()

        assertTrue(ScrollPassController.isRunning(state, settings, at(14 * minute)))
        assertEquals(minute, ScrollPassController.remainingMs(state, settings, at(14 * minute)))

        assertFalse(ScrollPassController.isRunning(state, settings, at(15 * minute)))
        assertEquals(ScrollPassPhase.COOLING, ScrollPassController.phase(state, settings, at(16 * minute)))
    }

    @Test
    fun `the allowance does not come back for a day`() {
        val state = started()

        assertEquals(ScrollPassPhase.COOLING, ScrollPassController.phase(state, settings, at(23 * hour)))
        assertEquals(hour, ScrollPassController.remainingMs(state, settings, at(23 * hour)))
        assertEquals(ScrollPassPhase.AVAILABLE, ScrollPassController.phase(state, settings, at(24 * hour)))
    }

    @Test
    fun `pressing the button again during the day does nothing`() {
        val state = started()

        val duringRun = ScrollPassController.start(state, at(5 * minute), settings)
        assertEquals(state.startedAtWallMs, duringRun.startedAtWallMs)

        val duringWait = ScrollPassController.start(state, at(3 * hour), settings)
        assertEquals(state.startedAtWallMs, duringWait.startedAtWallMs)
    }

    @Test
    fun `winding the clock forward does not buy a second allowance`() {
        val state = started()

        // The calendar claims a day has gone by; uptime says two minutes.
        val doctored = TimeReading(wallMs = 25 * hour, elapsedMs = 2 * minute)
        assertEquals(ScrollPassPhase.RUNNING, ScrollPassController.phase(state, settings, doctored))
        assertEquals(state.startedAtWallMs, ScrollPassController.start(state, doctored, settings).startedAtWallMs)
    }

    @Test
    fun `the state clears itself once the wait is genuinely over`() {
        val ticked = ScrollPassController.tick(started(), at(24 * hour), settings)
        assertFalse(ticked.progress.running)
        assertEquals(ScrollPassPhase.AVAILABLE, ScrollPassController.phase(ticked, settings, at(24 * hour)))
    }

    @Test
    fun `a checkpoint part way through changes nothing about when it ends`() {
        val halfway = ScrollPassController.tick(started(), at(7 * minute), settings)
        assertEquals(ScrollPassPhase.RUNNING, ScrollPassController.phase(halfway, settings, at(14 * minute)))
        assertEquals(ScrollPassPhase.COOLING, ScrollPassController.phase(halfway, settings, at(15 * minute)))
    }

    @Test
    fun `a disabled button is never available`() {
        val off = settings.copy(enabled = false)
        assertEquals(ScrollPassPhase.AVAILABLE, ScrollPassController.phase(ScrollPassState(), off))
        assertEquals(ScrollPassState(), ScrollPassController.start(ScrollPassState(), at(0), off))
    }

    @Test
    fun `a running pass lets the reels feed through, and only while it runs`() {
        val resolver = PolicyResolver()
        val classification = Classification(FeedSurface.SHORT_VIDEO_FEED, 100)
        val now = LocalDateTime.of(2026, 9, 15, 14, 0)

        val allowed = resolver.decide(
            GuardSettings(), SupportedApps.INSTAGRAM, classification, now, scrollPassRunning = true,
        )
        assertEquals(AllowReason.SCROLL_PASS, (allowed as GuardDecision.Allow).reason)

        val blocked = resolver.decide(
            GuardSettings(), SupportedApps.INSTAGRAM, classification, now, scrollPassRunning = false,
        )
        assertTrue(blocked is GuardDecision.Block)
    }

    @Test
    fun `the countdown reads the way a countdown should`() {
        val state = started()
        assertEquals("15:00", Durations.formatPrecise(ScrollPassController.remainingMs(state, settings, at(0))).removePrefix("00:"))
        assertEquals("23h 0m", Durations.format(ScrollPassController.remainingMs(state, settings, at(1 * hour))))
    }
}
