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

    fun decide(
        settings: GuardSettings,
        packageName: String,
        classification: Classification,
        now: LocalDateTime,
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

        // The app only ever removes short-video feeds. Messages, stories, posts, search and
        // long-form video are recognised so the guard knows where a reel was opened from, and
        // are structurally incapable of being blocked - no setting can change this.
        if (!classification.surface.isShortVideo) {
            return GuardDecision.Allow(AllowReason.NOT_SHORT_VIDEO, classification.surface)
        }

        val breakWindow = ScheduleEvaluator.activeWindow(settings.schedule, packageName, now)
        if (breakWindow != null) {
            return GuardDecision.Allow(
                reason = AllowReason.SCHEDULED_BREAK,
                surface = classification.surface,
                detail = breakWindow.label,
            )
        }

        return when (profile.actionFor(classification.surface)) {
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
        fun styleFor(profile: AppProfile, surface: FeedSurface): BlockStyle =
            if (surface == FeedSurface.SHORT_VIDEO_IN_HOME) BlockStyle.COVER else profile.blockStyle
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
