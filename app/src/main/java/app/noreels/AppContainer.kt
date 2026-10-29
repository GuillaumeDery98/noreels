package app.noreels

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.noreels.data.SettingsRepository
import app.noreels.data.StatsRepository
import app.noreels.data.UsageTimeRepository
import app.noreels.rules.BlockingRules
import app.noreels.rules.RulesParser
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noreels")

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val rules: BlockingRules = appContext.resources.openRawResource(R.raw.blocking_rules)
        .bufferedReader()
        .use { RulesParser.parse(it.readText()) }

    val settings = SettingsRepository(appContext.dataStore)

    val stats = StatsRepository(appContext.dataStore) { LocalDate.now() }

    val usage = UsageTimeRepository(appContext) { System.currentTimeMillis() }
}
