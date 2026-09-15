package com.antidoomscroller.core.lock

import com.antidoomscroller.core.model.ChallengeSettings
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class LockPhase {
    /** Filter on and locked. */
    ARMED,

    /** The user asked to turn it off; the wait is running. */
    COOLING,

    /** The wait is served; the challenge is the last gate. */
    CHALLENGE,

    /** Filter off. */
    DISABLED,
}

@Serializable
data class LockState(
    val phase: LockPhase = LockPhase.ARMED,
    val progress: ProgressState = ProgressState(),
    val requiredMs: Long = DEFAULT_COOLDOWN_MS,
    val requestedAtWallMs: Long = 0,
    val challengeSeed: Long = 0,
    val solvedCount: Int = 0,
    val failedAttempts: Int = 0,
    /** Monotonic accrual value the penalty expires at; not a wall-clock time, so it cannot be skipped. */
    val penaltyUntilAccruedMs: Long = 0,
    val disabledSinceWallMs: Long = 0,
) {
    val isFilterActive: Boolean get() = phase != LockPhase.DISABLED

    companion object {
        const val DEFAULT_COOLDOWN_MS: Long = 48 * 60 * 60 * 1000L
    }
}

/** What happened when an answer was submitted. */
sealed interface AnswerResult {
    data class Accepted(val state: LockState, val remainingProblems: Int) : AnswerResult
    data class Rejected(val state: LockState, val penaltyMs: Long) : AnswerResult
    data class Completed(val state: LockState) : AnswerResult
    data class NotReady(val state: LockState) : AnswerResult
}

/**
 * The 48 hour cooldown plus challenge, as a pure state machine.
 *
 * Turning the filter *on* is instant and needs nothing. Turning it *off* costs a full wait
 * measured on a clock the user cannot wind forward, then a set of problems that are unpleasant to
 * do in the exact moment someone wants to skip them. Cancelling the request at any point is free
 * and immediate - the friction only ever points one way.
 */
object LockController {

    fun requestDisable(
        state: LockState,
        now: TimeReading,
        cooldownMinutes: Int,
    ): LockState {
        if (state.phase == LockPhase.DISABLED) return state
        if (state.phase != LockPhase.ARMED) return state
        return state.copy(
            phase = LockPhase.COOLING,
            progress = CooldownProgress.start(now),
            requiredMs = cooldownMinutes.coerceIn(1, 14 * 24 * 60) * 60L * 1000L,
            requestedAtWallMs = now.wallMs,
            challengeSeed = 0,
            solvedCount = 0,
            failedAttempts = 0,
            penaltyUntilAccruedMs = 0,
        )
    }

    /** Re-locking is always free. */
    fun cancelRequest(state: LockState): LockState =
        if (state.phase == LockPhase.COOLING || state.phase == LockPhase.CHALLENGE) {
            LockState(phase = LockPhase.ARMED)
        } else {
            state
        }

    /** Turning the filter back on: instant, from any phase. */
    fun rearm(state: LockState): LockState = LockState(phase = LockPhase.ARMED)

    /** Called on a timer, on app open and on boot; moves the cooldown forward. */
    fun tick(state: LockState, now: TimeReading, seedSource: () -> Long = { Random.nextLong() }): LockState {
        if (state.phase != LockPhase.COOLING && state.phase != LockPhase.CHALLENGE) return state
        val advanced = CooldownProgress.advance(state.progress, now)
        var next = state.copy(progress = advanced.state)

        if (next.phase == LockPhase.COOLING &&
            CooldownProgress.isComplete(advanced.state, state.requiredMs, now)
        ) {
            next = next.copy(
                phase = LockPhase.CHALLENGE,
                challengeSeed = if (next.challengeSeed == 0L) seedSource() else next.challengeSeed,
                solvedCount = 0,
            )
        }
        return next
    }

    fun remainingMs(state: LockState, now: TimeReading): Long = when (state.phase) {
        LockPhase.COOLING -> CooldownProgress.remainingMs(state.progress, state.requiredMs, now)
        else -> 0
    }

    fun isPenaltyActive(state: LockState): Boolean =
        state.penaltyUntilAccruedMs > state.progress.accruedMs

    fun penaltyRemainingMs(state: LockState): Long =
        (state.penaltyUntilAccruedMs - state.progress.accruedMs).coerceAtLeast(0)

    fun challengeFor(state: LockState, settings: ChallengeSettings): Challenge? {
        if (state.phase != LockPhase.CHALLENGE || state.challengeSeed == 0L) return null
        return ChallengeGenerator.generate(state.challengeSeed, settings.problemCount, settings.difficulty)
    }

    /**
     * A wrong answer throws away the whole set and starts a short penalty, so guessing is worse
     * than working it out. The cooldown itself is never repeated - the wait was already served.
     */
    fun submitAnswer(
        state: LockState,
        input: String,
        settings: ChallengeSettings,
        seedSource: () -> Long = { Random.nextLong() },
    ): AnswerResult {
        if (state.phase != LockPhase.CHALLENGE) return AnswerResult.NotReady(state)
        if (isPenaltyActive(state)) return AnswerResult.NotReady(state)
        val challenge = challengeFor(state, settings) ?: return AnswerResult.NotReady(state)
        val problem = challenge.problems.getOrNull(state.solvedCount) ?: return AnswerResult.NotReady(state)

        if (!problem.isCorrect(input)) {
            val penaltyMs = settings.penaltyMinutesOnWrongAnswer.coerceAtLeast(0) * 60_000L
            val next = state.copy(
                solvedCount = 0,
                failedAttempts = state.failedAttempts + 1,
                challengeSeed = seedSource(),
                penaltyUntilAccruedMs = state.progress.accruedMs + penaltyMs,
            )
            return AnswerResult.Rejected(next, penaltyMs)
        }

        val solved = state.solvedCount + 1
        if (solved >= challenge.problems.size) {
            return AnswerResult.Completed(
                state.copy(
                    phase = LockPhase.DISABLED,
                    solvedCount = solved,
                    disabledSinceWallMs = state.progress.lastWallMs,
                    progress = state.progress.copy(running = false),
                ),
            )
        }
        return AnswerResult.Accepted(state.copy(solvedCount = solved), challenge.problems.size - solved)
    }
}
