package app.noreels.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.noreels.allowance.WatchedTotal
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WatchTimeRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private var today = LocalDate.of(2026, 11, 2)

    @Test
    fun recordsTodayTotal() = runTest(UnconfinedTestDispatcher()) {
        val repository = WatchTimeRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("watch.preferences_pb") }
        ) { today }

        repository.record(WatchedTotal(today, 42_000))

        assertThat(repository.watchedToday.first()).isEqualTo(42_000)
    }

    @Test
    fun newDayReadsZero() = runTest(UnconfinedTestDispatcher()) {
        val repository = WatchTimeRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("watch.preferences_pb") }
        ) { today }
        repository.record(WatchedTotal(today, 42_000))

        today = today.plusDays(1)

        assertThat(repository.watchedToday.first()).isEqualTo(0)
    }

    @Test
    fun recordPurgesOtherDays() = runTest(UnconfinedTestDispatcher()) {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("watch.preferences_pb") }
        val repository = WatchTimeRepository(dataStore) { today }
        repository.record(WatchedTotal(today, 42_000))

        repository.record(WatchedTotal(today.plusDays(1), 1_000))

        assertThat(dataStore.data.first().asMap().keys.map { it.name }).containsExactly("watched_ms_2026-11-03")
    }

    @Test
    fun statsBlocksIgnoreWatchedKeys() = runTest(UnconfinedTestDispatcher()) {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("shared.preferences_pb") }
        val watchTime = WatchTimeRepository(dataStore) { today }
        val stats = StatsRepository(dataStore) { today }
        watchTime.record(WatchedTotal(today, 42_000))

        stats.recordBlock("instagram")

        assertThat(watchTime.watchedToday.first()).isEqualTo(42_000)
        assertThat(stats.blocksToday.first()).containsExactly("instagram", 1)
    }
}
