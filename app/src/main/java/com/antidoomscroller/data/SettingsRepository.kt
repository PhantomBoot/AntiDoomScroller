package com.antidoomscroller.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.antidoomscroller.core.detect.DefaultSignatures
import com.antidoomscroller.core.detect.SignaturePack
import com.antidoomscroller.core.model.AppProfile
import com.antidoomscroller.core.model.GuardSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/**
 * The single source of truth for user settings.
 *
 * Reads are a hot [StateFlow] because the accessibility service has to answer "is this blocked"
 * synchronously, on the main thread, while a window is opening.
 */
class SettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val settings: StateFlow<GuardSettings> = context.guardDataStore.data
        .map { preferences -> decodeSettings(preferences[PrefKeys.SETTINGS]) }
        .stateIn(scope, SharingStarted.Eagerly, GuardSettings())

    val signatures: StateFlow<SignaturePack> = context.guardDataStore.data
        .map { preferences -> decodeSignatures(preferences[PrefKeys.SIGNATURES]) }
        .stateIn(scope, SharingStarted.Eagerly, DefaultSignatures.pack())

    /** Blocks until settings have actually been read from disk once. */
    suspend fun awaitLoaded(): GuardSettings =
        decodeSettings(context.guardDataStore.data.first()[PrefKeys.SETTINGS])

    suspend fun update(transform: (GuardSettings) -> GuardSettings) {
        context.guardDataStore.edit { preferences ->
            val current = decodeSettings(preferences[PrefKeys.SETTINGS])
            preferences[PrefKeys.SETTINGS] = json.encodeToString(GuardSettings.serializer(), transform(current))
        }
    }

    suspend fun updateProfile(packageName: String, transform: (AppProfile) -> AppProfile) {
        update { settings ->
            val profile = settings.profileFor(packageName) ?: return@update settings
            settings.withProfile(transform(profile))
        }
    }

    suspend fun saveSignatures(pack: SignaturePack) {
        context.guardDataStore.edit { preferences ->
            preferences[PrefKeys.SIGNATURES] = SignaturePack.encode(pack)
        }
    }

    suspend fun resetSignatures() {
        context.guardDataStore.edit { preferences ->
            preferences.remove(PrefKeys.SIGNATURES)
        }
    }

    private fun decodeSettings(raw: String?): GuardSettings = runCatching {
        raw?.let { json.decodeFromString(GuardSettings.serializer(), it) }
    }.getOrNull() ?: GuardSettings()

    private fun decodeSignatures(raw: String?): SignaturePack = runCatching {
        raw?.let { SignaturePack.parse(it) }
    }.getOrNull() ?: DefaultSignatures.pack()
}
