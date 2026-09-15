package com.antidoomscroller.core.lock

import kotlinx.serialization.Serializable

/** One reading of both clocks. Wall time can be edited by the user; elapsed time cannot. */
data class TimeReading(val wallMs: Long, val elapsedMs: Long)

/**
 * How much of the cooldown has genuinely been served.
 *
 * [accruedMs] only ever grows by monotonic time, so moving the system clock forward does not buy
 * progress. [wallStartMs] is kept as a second, independent condition.
 */
@Serializable
data class ProgressState(
    val running: Boolean = false,
    val accruedMs: Long = 0,
    val wallStartMs: Long = 0,
    val lastWallMs: Long = 0,
    val lastElapsedMs: Long = 0,
    val tamperEvents: Int = 0,
    val lastTamperWallMs: Long = 0,
)

data class AdvanceResult(
    val state: ProgressState,
    val creditedMs: Long,
    val tamperDetected: Boolean,
)

/**
 * Tamper-resistant elapsed-time accounting for the 48 hour cooldown.
 *
 * Two clocks are read at every checkpoint. Inside a single boot, monotonic uptime is the only
 * thing credited, so winding the clock forward does nothing. Across a reboot - where uptime
 * restarts and only wall time survives - credit is capped per boot, so rebooting with a doctored
 * clock cannot skip the wait either. Every disagreement between the two clocks is recorded and
 * adds a penalty, and it is shown to the user rather than hidden.
 */
object CooldownProgress {

    /** Most a single power-off gap can contribute, since only the editable clock witnesses it. */
    const val OFFLINE_CREDIT_CAP_MS: Long = 6 * 60 * 60 * 1000L

    /** Normal drift between the two clocks (NTP corrections, doze) that is not treated as tampering. */
    const val SKEW_TOLERANCE_MS: Long = 90 * 1000L

    /** Added to the requirement for each detected clock change. */
    const val TAMPER_PENALTY_MS: Long = 60 * 60 * 1000L

    fun start(now: TimeReading): ProgressState = ProgressState(
        running = true,
        accruedMs = 0,
        wallStartMs = now.wallMs,
        lastWallMs = now.wallMs,
        lastElapsedMs = now.elapsedMs,
    )

    fun advance(state: ProgressState, now: TimeReading): AdvanceResult {
        if (!state.running) return AdvanceResult(state, 0, false)

        val elapsedDelta = now.elapsedMs - state.lastElapsedMs
        val wallDelta = now.wallMs - state.lastWallMs

        val credited: Long
        val tampered: Boolean

        if (elapsedDelta < 0) {
            // Uptime went backwards: the device rebooted. Only wall time saw the gap, so cap it.
            credited = wallDelta.coerceIn(0, OFFLINE_CREDIT_CAP_MS)
            tampered = wallDelta < 0 || wallDelta > OFFLINE_CREDIT_CAP_MS + SKEW_TOLERANCE_MS
        } else {
            // Same boot: monotonic uptime is the truth, whatever the wall clock now says.
            credited = elapsedDelta
            tampered = kotlin.math.abs(wallDelta - elapsedDelta) > SKEW_TOLERANCE_MS
        }

        // A backwards jump would otherwise leave the wall-clock condition unreachable. Re-anchor
        // the start so the wait already served is kept - never more than that.
        val servedWallMs = (state.lastWallMs - state.wallStartMs).coerceAtLeast(0) + credited
        val wallStart = if (wallDelta < 0) now.wallMs - servedWallMs else state.wallStartMs

        val next = state.copy(
            accruedMs = state.accruedMs + credited,
            lastWallMs = now.wallMs,
            lastElapsedMs = now.elapsedMs,
            tamperEvents = if (tampered) state.tamperEvents + 1 else state.tamperEvents,
            lastTamperWallMs = if (tampered) now.wallMs else state.lastTamperWallMs,
            wallStartMs = wallStart,
        )
        return AdvanceResult(next, credited, tampered)
    }

    /** The requirement after clock-tampering penalties. */
    fun effectiveRequirementMs(state: ProgressState, baseRequirementMs: Long): Long =
        baseRequirementMs + state.tamperEvents * TAMPER_PENALTY_MS

    /**
     * Both conditions must hold: monotonic time actually served, and the calendar agrees that the
     * wait has passed.
     */
    fun isComplete(state: ProgressState, baseRequirementMs: Long, now: TimeReading): Boolean {
        if (!state.running) return false
        val required = effectiveRequirementMs(state, baseRequirementMs)
        val wallElapsed = now.wallMs - state.wallStartMs
        return state.accruedMs >= required && wallElapsed >= required
    }

    fun remainingMs(state: ProgressState, baseRequirementMs: Long, now: TimeReading): Long {
        if (!state.running) return baseRequirementMs
        val required = effectiveRequirementMs(state, baseRequirementMs)
        val byAccrued = required - state.accruedMs
        val byWall = required - (now.wallMs - state.wallStartMs)
        return maxOf(byAccrued, byWall).coerceAtLeast(0)
    }
}
