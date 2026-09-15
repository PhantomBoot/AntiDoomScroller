package com.antidoomscroller.core

import com.antidoomscroller.core.lock.AnswerResult
import com.antidoomscroller.core.lock.ChallengeGenerator
import com.antidoomscroller.core.lock.CooldownProgress
import com.antidoomscroller.core.lock.LockController
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.lock.LockState
import com.antidoomscroller.core.lock.PasswordHasher
import com.antidoomscroller.core.lock.TimeReading
import com.antidoomscroller.core.model.ChallengeDifficulty
import com.antidoomscroller.core.model.ChallengeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {

    private val hour = 60 * 60 * 1000L
    private val settings = ChallengeSettings(problemCount = 3, difficulty = ChallengeDifficulty.HARD)

    private fun reading(wallHours: Double, elapsedHours: Double) =
        TimeReading((wallHours * hour).toLong(), (elapsedHours * hour).toLong())

    // ---- cooldown accounting -------------------------------------------------

    @Test
    fun `progress accrues with real elapsed time`() {
        var state = CooldownProgress.start(reading(0.0, 0.0))
        state = CooldownProgress.advance(state, reading(10.0, 10.0)).state
        state = CooldownProgress.advance(state, reading(20.0, 20.0)).state
        assertEquals(20 * hour, state.accruedMs)
        assertEquals(0, state.tamperEvents)
    }

    @Test
    fun `winding the clock forward buys no progress`() {
        var state = CooldownProgress.start(reading(0.0, 0.0))
        // Wall clock jumps 48 hours, uptime says one minute has passed.
        val result = CooldownProgress.advance(state, TimeReading(48 * hour, 60_000))
        state = result.state

        assertEquals(60_000, state.accruedMs)
        assertTrue(result.tamperDetected)
        assertEquals(1, state.tamperEvents)
        assertFalse(CooldownProgress.isComplete(state, 48 * hour, TimeReading(48 * hour, 60_000)))
    }

    @Test
    fun `winding the clock backwards does not stall the wall clock condition forever`() {
        var state = CooldownProgress.start(reading(100.0, 0.0))
        state = CooldownProgress.advance(state, reading(90.0, 10.0)).state // clock pushed back 10h
        assertEquals(10 * hour, state.accruedMs)
        assertEquals(1, state.tamperEvents)
        // The start marker moved with it, so the wall-clock condition still tracks real time.
        assertEquals((90 - 10) * hour, state.wallStartMs)
    }

    @Test
    fun `a reboot credits capped offline time`() {
        var state = CooldownProgress.start(reading(0.0, 5.0))
        // Uptime restarts at 0 while the wall clock claims 40 hours went by.
        val result = CooldownProgress.advance(state, TimeReading(40 * hour, 30_000))
        state = result.state

        assertEquals(CooldownProgress.OFFLINE_CREDIT_CAP_MS, state.accruedMs)
        assertTrue(result.tamperDetected)
    }

    @Test
    fun `an ordinary overnight reboot is credited without a tamper flag`() {
        var state = CooldownProgress.start(reading(0.0, 3.0))
        val result = CooldownProgress.advance(state, TimeReading(5 * hour, 30_000))
        state = result.state
        assertEquals(5 * hour, state.accruedMs)
        assertFalse(result.tamperDetected)
    }

    @Test
    fun `completion needs both monotonic and wall clock agreement`() {
        var state = CooldownProgress.start(reading(0.0, 0.0))
        state = CooldownProgress.advance(state, reading(48.0, 48.0)).state
        assertTrue(CooldownProgress.isComplete(state, 48 * hour, reading(48.0, 48.0)))

        var slow = CooldownProgress.start(reading(0.0, 0.0))
        slow = CooldownProgress.advance(slow, reading(47.0, 47.0)).state
        assertFalse(CooldownProgress.isComplete(slow, 48 * hour, reading(47.0, 47.0)))
        assertEquals(hour, CooldownProgress.remainingMs(slow, 48 * hour, reading(47.0, 47.0)))
    }

    @Test
    fun `each detected clock change extends the requirement`() {
        val tampered = CooldownProgress.start(reading(0.0, 0.0)).copy(tamperEvents = 2)
        assertEquals(50 * hour, CooldownProgress.effectiveRequirementMs(tampered, 48 * hour))
    }

    // ---- the disable flow ----------------------------------------------------

    @Test
    fun `full disable flow takes the wait and then the challenge`() {
        var state = LockController.requestDisable(LockState(), reading(0.0, 0.0), cooldownMinutes = 48 * 60)
        assertEquals(LockPhase.COOLING, state.phase)

        state = LockController.tick(state, reading(24.0, 24.0)) { 42L }
        assertEquals(LockPhase.COOLING, state.phase)
        assertEquals(24 * hour, LockController.remainingMs(state, reading(24.0, 24.0)))

        state = LockController.tick(state, reading(48.0, 48.0)) { 42L }
        assertEquals(LockPhase.CHALLENGE, state.phase)

        val challenge = requireNotNull(LockController.challengeFor(state, settings))
        assertEquals(3, challenge.problems.size)

        challenge.problems.forEachIndexed { index, problem ->
            when (val result = LockController.submitAnswer(state, problem.answer.toString(), settings)) {
                is AnswerResult.Accepted -> {
                    state = result.state
                    assertEquals(2 - index, result.remainingProblems)
                }

                is AnswerResult.Completed -> {
                    state = result.state
                    assertEquals(2, index)
                }

                else -> error("unexpected $result")
            }
        }

        assertEquals(LockPhase.DISABLED, state.phase)
        assertFalse(state.isFilterActive)
    }

    @Test
    fun `a wrong answer restarts the set and starts a penalty`() {
        var state = LockController.requestDisable(LockState(), reading(0.0, 0.0), cooldownMinutes = 48 * 60)
        state = LockController.tick(state, reading(48.0, 48.0)) { 7L }
        val firstProblem = requireNotNull(LockController.challengeFor(state, settings)).problems.first()
        state = (LockController.submitAnswer(state, firstProblem.answer.toString(), settings) as AnswerResult.Accepted).state
        assertEquals(1, state.solvedCount)

        val rejected = LockController.submitAnswer(state, "-999999", settings) { 8L } as AnswerResult.Rejected
        state = rejected.state

        assertEquals(0, state.solvedCount)
        assertEquals(1, state.failedAttempts)
        assertEquals(15 * 60_000L, rejected.penaltyMs)
        assertTrue(LockController.isPenaltyActive(state))
        assertNotEquals(7L, state.challengeSeed)
        assertTrue(LockController.submitAnswer(state, "0", settings) is AnswerResult.NotReady)
    }

    @Test
    fun `the penalty is measured in monotonic time`() {
        var state = LockController.requestDisable(LockState(), reading(0.0, 0.0), cooldownMinutes = 48 * 60)
        state = LockController.tick(state, reading(48.0, 48.0)) { 11L }
        state = (LockController.submitAnswer(state, "wrong", settings) as AnswerResult.Rejected).state
        assertTrue(LockController.isPenaltyActive(state))

        // Wall clock jumps an hour; uptime says twenty minutes. Twenty minutes is what counts.
        state = LockController.tick(state, TimeReading(49 * hour, 48 * hour + 20 * 60_000))
        assertFalse(LockController.isPenaltyActive(state))
    }

    @Test
    fun `turning the filter back on is instant at every stage`() {
        var state = LockController.requestDisable(LockState(), reading(0.0, 0.0), cooldownMinutes = 48 * 60)
        assertEquals(LockPhase.ARMED, LockController.cancelRequest(state).phase)

        state = LockController.tick(state, reading(48.0, 48.0)) { 5L }
        assertEquals(LockPhase.CHALLENGE, state.phase)
        assertEquals(LockPhase.ARMED, LockController.cancelRequest(state).phase)

        val disabled = LockState(phase = LockPhase.DISABLED)
        assertEquals(LockPhase.ARMED, LockController.rearm(disabled).phase)
    }

    @Test
    fun `a second disable request cannot restart or shortcut the wait`() {
        val state = LockController.requestDisable(LockState(), reading(0.0, 0.0), cooldownMinutes = 48 * 60)
        val ticked = LockController.tick(state, reading(20.0, 20.0))
        val again = LockController.requestDisable(ticked, reading(20.0, 20.0), cooldownMinutes = 60)
        assertEquals(20 * hour, again.progress.accruedMs)
        assertEquals(48 * hour, again.requiredMs)
    }

    @Test
    fun `no challenge is available before the wait is served`() {
        val state = LockController.requestDisable(LockState(), reading(0.0, 0.0), cooldownMinutes = 48 * 60)
        assertEquals(null, LockController.challengeFor(state, settings))
        assertTrue(LockController.submitAnswer(state, "0", settings) is AnswerResult.NotReady)
    }

    @Test
    fun `the master switch gate is the same machinery at ten minutes`() {
        val gate = com.antidoomscroller.core.model.MasterLockSettings()
        assertTrue("the gate is on out of the box", gate.enabled)
        assertEquals(10, gate.cooldownMinutes)

        var state = LockController.requestDisable(LockState(), reading(0.0, 0.0), gate.cooldownMinutes)
        assertEquals(10 * 60_000L, state.requiredMs)

        // Nine minutes in, still waiting.
        state = LockController.tick(state, TimeReading(9 * 60_000, 9 * 60_000)) { 3L }
        assertEquals(LockPhase.COOLING, state.phase)

        state = LockController.tick(state, TimeReading(10 * 60_000, 10 * 60_000)) { 3L }
        assertEquals(LockPhase.CHALLENGE, state.phase)

        val challenge = requireNotNull(LockController.challengeFor(state, gate.challenge))
        assertEquals(3, challenge.problems.size)
        challenge.problems.forEach { assertTrue(it.prompt.isNotBlank()) }

        // And the clock is no more helpful here than it is on the filter.
        val doctored = LockController.requestDisable(LockState(), reading(0.0, 0.0), gate.cooldownMinutes)
        val jumped = LockController.tick(doctored, TimeReading(60 * 60_000, 30_000)) { 3L }
        assertEquals(LockPhase.COOLING, jumped.phase)
    }

    // ---- challenge generation ------------------------------------------------

    @Test
    fun `challenges are deterministic so they cannot be rerolled`() {
        val first = ChallengeGenerator.generate(seed = 99, count = 3, difficulty = ChallengeDifficulty.HARD)
        val second = ChallengeGenerator.generate(seed = 99, count = 3, difficulty = ChallengeDifficulty.HARD)
        assertEquals(first.problems.map { it.prompt }, second.problems.map { it.prompt })
        assertNotEquals(
            first.problems.map { it.prompt },
            ChallengeGenerator.generate(100, 3, ChallengeDifficulty.HARD).problems.map { it.prompt },
        )
    }

    @Test
    fun `generated answers are self consistent at every difficulty`() {
        for (difficulty in ChallengeDifficulty.entries) {
            for (seed in 1L..200L) {
                val challenge = ChallengeGenerator.generate(seed, 3, difficulty)
                assertEquals(3, challenge.problems.size)
                challenge.problems.forEach { problem ->
                    assertTrue(problem.prompt.isNotBlank())
                    assertTrue(problem.isCorrect(problem.answer.toString()))
                    assertTrue(problem.isCorrect("  ${problem.answer}  "))
                    assertFalse(problem.isCorrect("${problem.answer}1"))
                    assertFalse(problem.isCorrect(""))
                    assertFalse(problem.isCorrect("not a number"))
                }
            }
        }
    }

    // ---- strict mode password -----------------------------------------------

    @Test
    fun `password hashing verifies correctly and salts every hash`() {
        val hash = PasswordHasher.hash("correct horse battery".toCharArray())
        assertTrue(PasswordHasher.verify("correct horse battery".toCharArray(), hash))
        assertFalse(PasswordHasher.verify("wrong".toCharArray(), hash))
        assertFalse(PasswordHasher.verify("correct horse battery".toCharArray(), null))
        assertFalse(PasswordHasher.verify("x".toCharArray(), "garbage"))
        assertNotEquals(hash, PasswordHasher.hash("correct horse battery".toCharArray()))
    }
}
