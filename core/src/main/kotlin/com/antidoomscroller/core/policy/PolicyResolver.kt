package com.antidoomscroller.core.policy

import com.antidoomscroller.core.detect.Classification
import com.antidoomscroller.core.model.AppProfile
import com.antidoomscroller.core.model.BlockStyle
import com.antidoomscroller.core.model.FeedSurface
import com.antidoomscroller.core.model.GuardSettings
import com.antidoomscroller.core.model.RuleAction
import com.antidoomscroller.core.schedule.ScheduleEvaluator
import java.time.LocalDateTime

/**
 * Turns "what is on screen" into "block or allow".
 *
 * Pure and synchronous: the accessibility service calls this on every window change, so it must
 * never touch disk or the network.
 */
class PolicyResolver {

    /**
     * @param scrollPassRunning true while the user's rationed allowance is being spent, which
     *   pauses feed blocking exactly like a scheduled break. It never touches the adult filter.
     */
    fun decide(
        settings: GuardSettings,
        packageName: String,
        classification: Classification,
        now: LocalDateTime,
        scrollPassRunning: Boolean = false,
    ): GuardDecision {
        if (!settings.masterEnabled) {
            return GuardDecision.Allow(AllowReason.MASTER_SWITCH_OFF, classification.surface)
        }

        val profile = settings.profileFor(packageName)
        if (profile == null || !profile.enabled) {
            return GuardDecision.Allow(AllowReason.APP_NOT_GUARDED, classification.surface)
        }

        if (!classification.isRecognised) {
            return GuardDecision.Allow(AllowReason.SURFACE_UNRECOGNISED, FeedSurface.UNKNOWN)
        }

        // Only the short-video feeds and stories can be taken away, and stories only when that
        // switch is on. Messages, posts, search, profiles and long-form video are recognised so
        // the guard knows where the user is, and are structurally incapable of being blocked - no
        // setting can change that.
        if (!classification.surface.isBlockable) {
            return GuardDecision.Allow(AllowReason.NOT_BLOCKABLE, classification.surface)
        }

        if (scrollPassRunning) {
            return GuardDecision.Allow(
                reason = AllowReason.SCROLL_PASS,
                surface = classification.surface,
            )
        }

        val breakWindow = ScheduleEvaluator.activeWindow(settings.schedule, packageName, now)
        if (breakWindow != null) {
            return GuardDecision.Allow(
                reason = AllowReason.SCHEDULED_BREAK,
                surface = classification.surface,
                detail = breakWindow.label,
            )
        }

        return when (profile.actionFor(governingSurface(classification.surface))) {
            RuleAction.ALLOW -> GuardDecision.Allow(AllowReason.SURFACE_ALLOWED, classification.surface)
            RuleAction.BLOCK -> GuardDecision.Block(
                packageName = packageName,
                surface = classification.surface,
                style = styleFor(profile, classification.surface),
                evidence = classification.evidence,
            )
        }
    }

    companion object {
        /**
         * How a blocked surface should be taken away.
         *
         * The app's choice applies to full-screen players, where backing out lands on the
         * previous tab. A short-video unit embedded in an ordinary feed is always covered
         * instead: backing out of it would mean backing out of the home feed, which is not what
         * was asked for.
         */
        /**
         * Which switch governs a surface.
         *
         * The Explore page answers to the same switch as a reel opened from it. Explore is a wall
         * of short video whichever way it is entered, so one switch covering both is what people
         * actually mean by turning it off.
         */
        fun governingSurface(surface: FeedSurface): FeedSurface =
            if (surface == FeedSurface.EXPLORE) FeedSurface.SHORT_VIDEO_IN_EXPLORE else surface

        fun styleFor(profile: AppProfile, surface: FeedSurface): BlockStyle = when (surface) {
            // Covering a story would leave it playing and advancing behind the panel, so the
            // only way to actually stop one is to step back out of the viewer.
            FeedSurface.STORIES -> BlockStyle.EXIT
            // The page is the problem, not one tile on it, so it is covered whole.
            FeedSurface.EXPLORE -> BlockStyle.COVER
            FeedSurface.SHORT_VIDEO_IN_HOME -> BlockStyle.COVER
            else -> profile.blockStyle
        }
    }
}

/**
 * Counts how many times backing out of a screen has been tried, so the guard can stop.
 *
 * Some apps put the short-video player straight back when you leave it. Pressing back forever
 * would walk the user out of the app entirely, so the budget runs out and the caller falls back
 * to covering the video instead.
 */
class BackOffBudget(
    private val attempts: Int,
    private val windowMs: Long,
) {
    private val recent = ArrayDeque<Long>()
    private var lastKey: String? = null

    /** Records a back press. Returns true once the budget for this screen is spent. */
    fun onBackPress(key: String, nowMs: Long): Boolean {
        if (key != lastKey) {
            recent.clear()
            lastKey = key
        }
        while (recent.isNotEmpty() && nowMs - recent.first() > windowMs) recent.removeFirst()
        recent.addLast(nowMs)
        return recent.size >= attempts
    }

    fun reset() {
        recent.clear()
        lastKey = null
    }
}
