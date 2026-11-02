package app.noreels.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import app.noreels.allowance.WatchedTotal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Persists the short-video time watched per local day; only the latest written day is kept. */
class WatchTimeRepository(
    private val dataStore: DataStore<Preferences>,
    private val today: () -> LocalDate,
) {

    val watchedToday: Flow<Long> = dataStore.data.map { it[keyFor(today())] ?: 0L }

    suspend fun record(total: WatchedTotal) {
        val key = keyFor(total.day)
        dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith(KEY_PREFIX) && it.name != key.name }
                .forEach { prefs.remove(it) }
            prefs[key] = total.totalMs
        }
    }

    private fun keyFor(day: LocalDate) = longPreferencesKey("$KEY_PREFIX$day")

    private companion object {
        /** Must not start with the stats prefix ("blocks_"), which StatsRepository purges. */
        const val KEY_PREFIX = "watched_ms_"
    }
}
