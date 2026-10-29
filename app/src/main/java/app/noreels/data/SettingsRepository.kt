package app.noreels.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores the surfaces the user switched off, so every surface is enabled by default. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val disabledSurfaces: Flow<Set<String>> =
        dataStore.data.map { it[DISABLED_SURFACES].orEmpty() }

    suspend fun setSurfaceEnabled(key: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[DISABLED_SURFACES].orEmpty()
            prefs[DISABLED_SURFACES] = if (enabled) current - key else current + key
        }
    }

    private companion object {
        val DISABLED_SURFACES = stringSetPreferencesKey("disabled_surfaces")
    }
}
