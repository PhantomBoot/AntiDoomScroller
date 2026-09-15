package com.antidoomscroller.core

import com.antidoomscroller.core.detect.Classification
import com.antidoomscroller.core.model.BlockStyle
import com.antidoomscroller.core.model.BreakWindow
import com.antidoomscroller.core.model.DefaultProfiles
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.GuardSettings
import com.antidoomscroller.core.model.RuleAction
import com.antidoomscroller.core.model.ScheduleSettings
import com.antidoomscroller.core.model.SupportedApps
import com.antidoomscroller.core.policy.AllowReason
import com.antidoomscroller.core.policy.BackOffBudget
import com.antidoomscroller.core.policy.GuardDecision
import com.antidoomscroller.core.policy.PolicyResolver
import com.antidoomscroller.core.schedule.ScheduleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PolicyTest {

    private val resolver = PolicyResolver()
    private val monday10am: LocalDateTime = LocalDateTime.of(2026, 9, 14, 10, 0)

    private fun decide(
        settings: GuardSettings,
        surface: FeedSurface,
        packageName: String = SupportedApps.INSTAGRAM,
        now: LocalDateTime = monday10am,
    ) = resolver.decide(settings, packageName, Classification(surface, 100), now)

    @Test
    fun `reels tab is blocked by default`() {
        val decision = decide(GuardSettings(), FeedSurface.SHORT_VIDEO_FEED)
        assertTrue(decision is GuardDecision.Block)
        assertEquals(FeedSurface.SHORT_VIDEO_FEED, (decision as GuardDecision.Block).surface)
    }

    @Test
    fun `reels in dms are allowed by default and blockable by switch`() {
        val allowed = decide(GuardSettings(), FeedSurface.SHORT_VIDEO_IN_DM)
        assertEquals(AllowReason.SURFACE_ALLOWED, (allowed as GuardDecision.Allow).reason)

        val settings = GuardSettings().let { base ->
            val profile = base.profileFor(SupportedApps.INSTAGRAM)!!
                .withAction(FeedSurface.SHORT_VIDEO_IN_DM, RuleAction.BLOCK)
            base.withProfile(profile)
        }
        assertTrue(decide(settings, FeedSurface.SHORT_VIDEO_IN_DM) is GuardDecision.Block)
    }

    @Test
    fun `everything that is not short video is untouchable, even if a rule says otherwise`() {
        val settings = GuardSettings().let { base ->
            var profile = base.profileFor(SupportedApps.INSTAGRAM)!!
            listOf(FeedSurface.EXPLORE, FeedSurface.HOME_FEED, FeedSurface.STORIES).forEach {
                profile = profile.withAction(it, RuleAction.BLOCK)
            }
            base.withProfile(profile)
        }

        listOf(FeedSurface.EXPLORE, FeedSurface.HOME_FEED, FeedSurface.STORIES).forEach { surface ->
            val decision = decide(settings, surface)
            assertTrue("$surface must never be blocked", decision is GuardDecision.Allow)
            assertEquals(AllowReason.NOT_SHORT_VIDEO, (decision as GuardDecision.Allow).reason)
        }
    }

    @Test
    fun `only the four short video surfaces are ever blockable`() {
        val blockable = FeedSurface.entries.filter { it.isShortVideo }
        assertEquals(
            listOf(
                FeedSurface.SHORT_VIDEO_FEED,
                FeedSurface.SHORT_VIDEO_IN_DM,
                FeedSurface.SHORT_VIDEO_IN_HOME,
                FeedSurface.SHORT_VIDEO_IN_EXPLORE,
            ),
            blockable,
        )
        assertTrue(DefaultProfiles.all().all { profile ->
            DefaultProfiles.configurableSurfaces(profile.packageName).all { it.isShortVideo }
        })
    }

    @Test
    fun `each app carries how its block should be applied`() {
        val instagram = decide(GuardSettings(), FeedSurface.SHORT_VIDEO_FEED) as GuardDecision.Block
        assertEquals(BlockStyle.COVER, instagram.style)

        val youtube = decide(GuardSettings(), FeedSurface.SHORT_VIDEO_FEED, SupportedApps.YOUTUBE) as GuardDecision.Block
        assertEquals(BlockStyle.EXIT, youtube.style)
    }

    @Test
    fun `master switch and per app switch stop enforcement`() {
        assertEquals(
            AllowReason.MASTER_SWITCH_OFF,
            (decide(GuardSettings(masterEnabled = false), FeedSurface.SHORT_VIDEO_FEED) as GuardDecision.Allow).reason,
        )

        val disabledApp = GuardSettings().let { base ->
            base.withProfile(base.profileFor(SupportedApps.INSTAGRAM)!!.copy(enabled = false))
        }
        assertEquals(
            AllowReason.APP_NOT_GUARDED,
            (decide(disabledApp, FeedSurface.SHORT_VIDEO_FEED) as GuardDecision.Allow).reason,
        )
    }

    @Test
    fun `unrecognised screens are never blocked`() {
        assertEquals(
            AllowReason.SURFACE_UNRECOGNISED,
            (decide(GuardSettings(), FeedSurface.UNKNOWN) as GuardDecision.Allow).reason,
        )
    }

    @Test
    fun `a scheduled break pauses blocking and only inside the window`() {
        val settings = GuardSettings(
            schedule = ScheduleSettings(
                enabled = true,
                windows = listOf(
                    BreakWindow(
                        id = "sunday",
                        label = "Sunday evening",
                        days = setOf(7),
                        startMinuteOfDay = 19 * 60,
                        endMinuteOfDay = 20 * 60,
                    ),
                ),
            ),
        )
        val sundayEvening = LocalDateTime.of(2026, 9, 20, 19, 30)
        val sundayLater = LocalDateTime.of(2026, 9, 20, 21, 0)

        val paused = decide(settings, FeedSurface.SHORT_VIDEO_FEED, now = sundayEvening)
        assertEquals(AllowReason.SCHEDULED_BREAK, (paused as GuardDecision.Allow).reason)
        assertEquals("Sunday evening", paused.detail)
        assertTrue(decide(settings, FeedSurface.SHORT_VIDEO_FEED, now = sundayLater) is GuardDecision.Block)
    }

    @Test
    fun `break windows can wrap past midnight`() {
        val window = BreakWindow(
            id = "late",
            days = setOf(5),
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 30,
        )
        assertTrue(window.wrapsMidnight)
        assertTrue(ScheduleEvaluator.isActive(window, LocalDateTime.of(2026, 9, 18, 23, 0)))
        assertTrue(ScheduleEvaluator.isActive(window, LocalDateTime.of(2026, 9, 19, 0, 15)))
        assertFalse(ScheduleEvaluator.isActive(window, LocalDateTime.of(2026, 9, 19, 1, 0)))
        assertFalse(ScheduleEvaluator.isActive(window, LocalDateTime.of(2026, 9, 17, 23, 0)))
    }

    @Test
    fun `schedule formatting is readable`() {
        assertEquals("09:05", ScheduleEvaluator.formatMinute(545))
        assertEquals("00:00", ScheduleEvaluator.formatMinute(0))
    }

    @Test
    fun `the back press budget runs out after the allowed attempts`() {
        val budget = BackOffBudget(attempts = 3, windowMs = 10_000)
        assertFalse(budget.onBackPress("yt/shorts", 0))
        assertFalse(budget.onBackPress("yt/shorts", 1_000))
        assertTrue(budget.onBackPress("yt/shorts", 2_000))
    }

    @Test
    fun `the budget forgets old and unrelated attempts`() {
        val budget = BackOffBudget(attempts = 3, windowMs = 5_000)
        budget.onBackPress("yt/shorts", 0)
        budget.onBackPress("yt/shorts", 1_000)
        assertFalse(budget.onBackPress("yt/shorts", 20_000))
        assertFalse(budget.onBackPress("ig/reels", 20_100))
    }

    @Test
    fun `a shorts shelf in a feed is covered even when the app backs out of players`() {
        val youtube = GuardSettings().profileFor(SupportedApps.YOUTUBE)!!
        assertEquals(BlockStyle.EXIT, youtube.blockStyle)

        // Backing out of the home feed because a shorts shelf is on it would take away the feed.
        assertEquals(
            BlockStyle.COVER,
            PolicyResolver.styleFor(youtube, FeedSurface.SHORT_VIDEO_IN_HOME),
        )
        assertEquals(
            BlockStyle.EXIT,
            PolicyResolver.styleFor(youtube, FeedSurface.SHORT_VIDEO_FEED),
        )

        val decision = decide(GuardSettings(), FeedSurface.SHORT_VIDEO_IN_HOME, SupportedApps.YOUTUBE)
        assertEquals(BlockStyle.COVER, (decision as GuardDecision.Block).style)
    }
}
