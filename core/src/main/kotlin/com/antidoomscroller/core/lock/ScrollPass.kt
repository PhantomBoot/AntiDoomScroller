package com.antidoomscroller.core.lock

import com.antidoomscroller.core.model.ScrollPassSettings
import kotlinx.serialization.Serializable

/**
 * A deliberate, rationed helping of the thing the app otherwise takes away.
 *
 * Tapping the button buys a fixed run of unblocked scrolling - and spends the day's allowance the
 * moment it starts. That is the point: an escape hatch nobody can reach for twice in an afternoon
 * is a decision made once, rather than a negotiation held every time the feed is missed.
 *
 * The run and the wait afterwards are both measured by [CooldownProgress], so the phone's clock
 * cannot be wound forward to get a second one.
 */
@Serializable
data class ScrollPassState(
    val progress: ProgressState = ProgressState(),
    /** When the allowance was last taken, for display only. */
    val startedAtWallMs: Long = 0,
)

enum class ScrollPassPhase {
    /** The button can be pressed. */
    AVAILABLE,

    /** Scrolling is unblocked right now. */
    RUNNING,

    /** The allowance is spent and blocking is back on. */
    COOLING,
}

object ScrollPassController {

    fun start(state: ScrollPassState, now: TimeReading, settings: ScrollPassSettings): ScrollPassState {
        if (!settings.enabled) return state
        if (phase(state, settings) != ScrollPassPhase.AVAILABLE) return state
        return ScrollPassState(
            progress = CooldownProgress.start(now),
            startedAtWallMs = now.wallMs,
        )
    }

    /** Persists the time served so far. Safe to call at any interval, or not at all. */
    fun tick(state: ScrollPassState, now: TimeReading, settings: ScrollPassSettings): ScrollPassState {
        if (!state.progress.running) return state
        val advanced = CooldownProgress.advance(state.progress, now).state
        // Once the wait is over there is nothing left to measure; go back to a clean slate.
        if (advanced.accruedMs >= cooldownMs(settings)) return ScrollPassState()
        return state.copy(progress = advanced)
    }

    /**
     * The phase as of [now], projected from the stored progress rather than read off it.
     *
     * Progress is only written to disk occasionally, so reading the stored value directly would
     * leave a finished allowance looking like it was still running until the next checkpoint.
     */
    fun phase(state: ScrollPassState, settings: ScrollPassSettings, now: TimeReading? = null): ScrollPassPhase {
        if (!settings.enabled || !state.progress.running) return ScrollPassPhase.AVAILABLE
        val accrued = accruedAt(state, now)
        return when {
            accrued < durationMs(settings) -> ScrollPassPhase.RUNNING
            accrued < cooldownMs(settings) -> ScrollPassPhase.COOLING
            else -> ScrollPassPhase.AVAILABLE
        }
    }

    fun isRunning(state: ScrollPassState, settings: ScrollPassSettings, now: TimeReading? = null): Boolean =
        phase(state, settings, now) == ScrollPassPhase.RUNNING

    /** Time left in the current run, or until the button comes back. Zero when available. */
    fun remainingMs(state: ScrollPassState, settings: ScrollPassSettings, now: TimeReading? = null): Long {
        val accrued = accruedAt(state, now)
        return when (phase(state, settings, now)) {
            ScrollPassPhase.RUNNING -> (durationMs(settings) - accrued).coerceAtLeast(0)
            ScrollPassPhase.COOLING -> (cooldownMs(settings) - accrued).coerceAtLeast(0)
            ScrollPassPhase.AVAILABLE -> 0
        }
    }

    private fun accruedAt(state: ScrollPassState, now: TimeReading?): Long =
        if (now == null) {
            state.progress.accruedMs
        } else {
            CooldownProgress.advance(state.progress, now).state.accruedMs
        }

    private fun durationMs(settings: ScrollPassSettings): Long =
        settings.durationMinutes.coerceIn(1, 240) * 60_000L

    private fun cooldownMs(settings: ScrollPassSettings): Long =
        settings.cooldownHours.coerceIn(1, 14 * 24) * 60L * 60L * 1000L
}
