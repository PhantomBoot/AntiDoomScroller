package com.antidoomscroller.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Two files, both in the app's private storage: what the user configured, and how far the
 * adult-filter cooldown has run. Neither is included in backups (see data_extraction_rules.xml),
 * so a "filter off" state cannot be restored onto a fresh phone.
 */
internal val Context.guardDataStore by preferencesDataStore(name = "guard_settings")
internal val Context.lockDataStore by preferencesDataStore(name = "filter_lock")

internal object PrefKeys {
    val SETTINGS: Preferences.Key<String> = stringPreferencesKey("settings_json")
    val SIGNATURES: Preferences.Key<String> = stringPreferencesKey("signature_pack_json")
    val LOCK_STATE: Preferences.Key<String> = stringPreferencesKey("lock_state_json")
}
