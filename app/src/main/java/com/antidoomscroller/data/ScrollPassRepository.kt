package com.antidoomscroller.data

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import com.antidoomscroller.core.lock.ScrollPassController
import com.antidoomscroller.core.lock.ScrollPassPhase
import com.antidoomscroller.core.lock.ScrollPassState
import com.antidoomscroller.core.lock.TimeReading
import com.antidoomscroller.core.model.ScrollPassSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Owns the rationed scrolling allowance.
 *
 * Stored next to the filter lock and measured the same way - against uptime rather than the
 * calendar - so a second helping cannot be had by changing the clock.
 */
class ScrollPassRepository(
    private val context: Context,
    scope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writeLock = Mutex()

    val state: StateFlow<ScrollPassState> = context.lockDataStore.data
        .map { preferences -> decode(preferences[PrefKeys.SCROLL_PASS]) }
        .stateIn(scope, SharingStarted.Eagerly, ScrollPassState())

    fun now(): TimeReading = TimeReading(System.currentTimeMillis(), SystemClock.elapsedRealtime())

    /** Spends the allowance. Does nothing if it is already running or still on its wait. */
    suspend fun start(settings: ScrollPassSettings): ScrollPassState =
        mutate { ScrollPassController.start(it, now(), settings) }

    suspend fun checkpoint(settings: ScrollPassSettings): ScrollPassState =
        mutate { ScrollPassController.tick(it, now(), settings) }

    /** The live phase, projected to this instant rather than read off the last checkpoint. */
    fun phase(settings: ScrollPassSettings): ScrollPassPhase =
        ScrollPassController.phase(state.value, settings, now())

    fun isRunning(settings: ScrollPassSettings): Boolean =
        ScrollPassController.isRunning(state.value, settings, now())

    fun remainingMs(settings: ScrollPassSettings): Long =
        ScrollPassController.remainingMs(state.value, settings, now())

    private suspend fun mutate(transform: (ScrollPassState) -> ScrollPassState): ScrollPassState =
        writeLock.withLock {
            var updated = ScrollPassState()
            context.lockDataStore.edit { preferences ->
                updated = transform(decode(preferences[PrefKeys.SCROLL_PASS]))
                preferences[PrefKeys.SCROLL_PASS] = json.encodeToString(ScrollPassState.serializer(), updated)
            }
            updated
        }

    private fun decode(raw: String?): ScrollPassState = runCatching {
        raw?.let { json.decodeFromString(ScrollPassState.serializer(), it) }
    }.getOrNull() ?: ScrollPassState()
}
