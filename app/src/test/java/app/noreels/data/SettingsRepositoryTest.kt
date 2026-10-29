package app.noreels.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("settings.preferences_pb") }
    )

    @Test
    fun defaultsToAllEnabled() = runTest(UnconfinedTestDispatcher()) {
        assertThat(repository().disabledSurfaces.first()).isEmpty()
    }

    @Test
    fun disableThenEnable() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        repository.setSurfaceEnabled("instagram.reels", false)
        assertThat(repository.disabledSurfaces.first()).containsExactly("instagram.reels")

        repository.setSurfaceEnabled("instagram.reels", true)
        assertThat(repository.disabledSurfaces.first()).isEmpty()
    }
}
