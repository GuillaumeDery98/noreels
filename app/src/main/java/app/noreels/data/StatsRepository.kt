package app.noreels.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Counts blocks per platform for the current local day; older days are purged on write. */
class StatsRepository(
    private val dataStore: DataStore<Preferences>,
    private val today: () -> LocalDate,
) {

    val blocksToday: Flow<Map<String, Int>> = dataStore.data.map { prefs ->
        val prefix = dayPrefix(today())
        prefs.asMap().entries
            .filter { it.key.name.startsWith(prefix) }
            .associate { it.key.name.removePrefix(prefix) to it.value as Int }
    }

    suspend fun recordBlock(platformId: String) {
        val prefix = dayPrefix(today())
        dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(KEY_PREFIX) && !it.name.startsWith(prefix) }
                .forEach { prefs.remove(it) }
            val key = intPreferencesKey(prefix + platformId)
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    private fun dayPrefix(day: LocalDate) = "${KEY_PREFIX}${day}_"

    private companion object {
        const val KEY_PREFIX = "blocks_"
    }
}
