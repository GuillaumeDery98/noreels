package app.noreels.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.noreels.AppContainer
import app.noreels.service.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val container: AppContainer,
    private val appContext: Context,
) : ViewModel() {

    /** Values read from the system on demand, refreshed each time the screen resumes. */
    private data class SystemSnapshot(
        val serviceEnabled: Boolean,
        val usage: Map<String, Long>?,
        val installed: Set<String>,
    )

    private val platformPackages = container.rules.platforms.flatMap { it.packages }.toSet()
    private val snapshot = MutableStateFlow(SystemSnapshot(serviceEnabled = false, usage = null, installed = emptySet()))

    val uiState: StateFlow<HomeUiState> = combine(
        container.settings.disabledSurfaces,
        container.stats.blocksToday,
        snapshot,
        container.settings.dailyAllowanceMinutes,
        container.watchTime.watchedToday,
    ) { disabled, blocks, system, allowanceMinutes, watchedMs ->
        buildHomeUiState(
            container.rules, disabled, blocks, system.usage, system.installed, system.serviceEnabled,
            allowanceMinutes, watchedMs,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        buildHomeUiState(
            container.rules, emptySet(), emptyMap(), null, emptySet(), serviceEnabled = false,
            allowanceMinutes = 0, watchedMs = 0,
        ),
    )

    fun refresh() {
        viewModelScope.launch {
            snapshot.value = withContext(Dispatchers.IO) {
                SystemSnapshot(
                    serviceEnabled = ServiceStatus.isEnabled(appContext),
                    usage = if (container.usage.hasAccess()) container.usage.todayForegroundMillis(platformPackages) else null,
                    installed = platformPackages.filter { appContext.packageManager.getLaunchIntentForPackage(it) != null }.toSet(),
                )
            }
        }
    }

    fun setSurfaceEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch { container.settings.setSurfaceEnabled(key, enabled) }
    }

    fun setDailyAllowance(minutes: Int) {
        viewModelScope.launch { container.settings.setDailyAllowanceMinutes(minutes) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
