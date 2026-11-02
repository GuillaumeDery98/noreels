package app.noreels.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores the surfaces the user switched off (every surface is enabled by default) and the daily
 * short-video allowance (0 by default, which blocks immediately).
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val disabledSurfaces: Flow<Set<String>> =
        dataStore.data.map { it[DISABLED_SURFACES].orEmpty() }

    suspend fun setSurfaceEnabled(key: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[DISABLED_SURFACES].orEmpty()
            prefs[DISABLED_SURFACES] = if (enabled) current - key else current + key
        }
    }

    val dailyAllowanceMinutes: Flow<Int> =
        dataStore.data.map { it[DAILY_ALLOWANCE_MINUTES] ?: 0 }

    suspend fun setDailyAllowanceMinutes(minutes: Int) {
        require(minutes in 0..MAX_ALLOWANCE_MINUTES) { "Allowance must be within 0..$MAX_ALLOWANCE_MINUTES minutes" }
        dataStore.edit { it[DAILY_ALLOWANCE_MINUTES] = minutes }
    }

    companion object {
        /** 23 h 59, the largest value the hours/minutes input accepts. */
        const val MAX_ALLOWANCE_MINUTES = 23 * 60 + 59

        private val DISABLED_SURFACES = stringSetPreferencesKey("disabled_surfaces")
        private val DAILY_ALLOWANCE_MINUTES = intPreferencesKey("daily_allowance_minutes")
    }
}
