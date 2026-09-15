package com.antidoomscroller.core.scroll

import com.antidoomscroller.core.model.AntiScrollSettings

/** What the detector wants the service to do after a scroll event. */
sealed interface ScrollVerdict {
    data object Nothing : ScrollVerdict
    data class Interrupt(val scrollsInWindow: Int, val windowSeconds: Int) : ScrollVerdict
}

/**
 * Anti-scroll mode.
 *
 * Counts scroll events inside a sliding window; past the threshold it asks for one interruption,
 * then stays quiet for the cooldown so it stays a nudge rather than noise. The counter is per app
 * and resets when the user leaves.
 */
class ScrollDetector {

    private val events = ArrayDeque<Long>()
    private var currentPackage: String? = null
    private var lastInterruptAtMs = Long.MIN_VALUE

    fun onScroll(packageName: String, settings: AntiScrollSettings, nowMs: Long): ScrollVerdict {
        if (!settings.enabled) return ScrollVerdict.Nothing

        if (packageName != currentPackage) {
            currentPackage = packageName
            events.clear()
            lastInterruptAtMs = Long.MIN_VALUE
        }

        val windowMs = settings.windowSeconds * 1_000L
        events.addLast(nowMs)
        while (events.isNotEmpty() && nowMs - events.first() > windowMs) events.removeFirst()

        if (events.size < settings.scrollThreshold) return ScrollVerdict.Nothing

        val cooldownMs = settings.cooldownSeconds * 1_000L
        if (lastInterruptAtMs != Long.MIN_VALUE && nowMs - lastInterruptAtMs < cooldownMs) {
            return ScrollVerdict.Nothing
        }

        lastInterruptAtMs = nowMs
        events.clear()
        return ScrollVerdict.Interrupt(settings.scrollThreshold, settings.windowSeconds)
    }

    /** Called when the user leaves the guarded app. */
    fun onLeaveApp() {
        events.clear()
        currentPackage = null
    }

    fun scrollsInWindow(): Int = events.size
}
