package app.noreels.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
class StatsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private var today = LocalDate.of(2026, 10, 29)

    @Test
    fun recordsPerPlatform() = runTest(UnconfinedTestDispatcher()) {
        val repository = StatsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("stats.preferences_pb") }
        ) { today }

        repository.recordBlock("instagram")
        repository.recordBlock("instagram")
        repository.recordBlock("youtube")

        assertThat(repository.blocksToday.first()).containsExactly("instagram", 2, "youtube", 1)
    }

    @Test
    fun countsResetOnNewDay() = runTest(UnconfinedTestDispatcher()) {
        val repository = StatsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("stats.preferences_pb") }
        ) { today }
        repository.recordBlock("instagram")

        today = today.plusDays(1)

        assertThat(repository.blocksToday.first()).isEmpty()
        repository.recordBlock("youtube")
        assertThat(repository.blocksToday.first()).containsExactly("youtube", 1)
    }
}
