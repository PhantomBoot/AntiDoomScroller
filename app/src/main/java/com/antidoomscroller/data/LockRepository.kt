package com.antidoomscroller.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import android.os.SystemClock
import com.antidoomscroller.core.lock.AnswerResult
import com.antidoomscroller.core.lock.Challenge
import com.antidoomscroller.core.lock.LockController
import com.antidoomscroller.core.lock.LockPhase
import com.antidoomscroller.core.lock.LockState
import com.antidoomscroller.core.lock.TimeReading
import com.antidoomscroller.core.model.ChallengeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Owns the adult filter's on/off state and the 48 hour cooldown in front of turning it off.
 *
 * Every read pairs the wall clock with monotonic uptime, which is what makes winding the clock
 * forward useless. See [com.antidoomscroller.core.lock.CooldownProgress].
 */
class LockRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writeLock = Mutex()

    val state: StateFlow<LockState> = context.lockDataStore.data
        .map { preferences -> decode(preferences[PrefKeys.LOCK_STATE]) }
        .stateIn(scope, SharingStarted.Eagerly, LockState())

    fun now(): TimeReading = TimeReading(System.currentTimeMillis(), SystemClock.elapsedRealtime())

    /** Called on a timer, on app open and on boot. Moves the cooldown forward and nothing else. */
    suspend fun checkpoint(): LockState = mutate { LockController.tick(it, now()) }

    suspend fun requestDisable(cooldownHours: Int): LockState =
        mutate { LockController.tick(LockController.requestDisable(it, now(), cooldownHours), now()) }

    /** Re-locking is free at every stage; that asymmetry is the whole point. */
    suspend fun cancelDisableRequest(): LockState = mutate { LockController.cancelRequest(it) }

    suspend fun rearm(): LockState = mutate { LockController.rearm(it) }

    fun challenge(settings: ChallengeSettings): Challenge? =
        LockController.challengeFor(state.value, settings)

    /** Returns the result so the UI can say what happened, and persists whatever state came back. */
    suspend fun submitAnswer(input: String, settings: ChallengeSettings): AnswerResult {
        var outcome: AnswerResult? = null
        mutate { current ->
            val ticked = LockController.tick(current, now())
            val result = LockController.submitAnswer(ticked, input, settings)
            outcome = result
            when (result) {
                is AnswerResult.Accepted -> result.state
                is AnswerResult.Rejected -> result.state
                is AnswerResult.Completed -> result.state
                is AnswerResult.NotReady -> ticked
            }
        }
        return outcome ?: AnswerResult.NotReady(state.value)
    }

    fun isFilterActive(): Boolean = state.value.phase != LockPhase.DISABLED

    private suspend fun mutate(transform: (LockState) -> LockState): LockState = writeLock.withLock {
        var updated = LockState()
        context.lockDataStore.edit { preferences ->
            updated = transform(decode(preferences[PrefKeys.LOCK_STATE]))
            preferences[PrefKeys.LOCK_STATE] = json.encodeToString(LockState.serializer(), updated)
        }
        updated
    }

    private fun decode(raw: String?): LockState = runCatching {
        raw?.let { json.decodeFromString(LockState.serializer(), it) }
    }.getOrNull() ?: LockState()
}
