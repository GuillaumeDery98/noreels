package app.noreels.ui

import app.noreels.rules.BlockingRules
import app.noreels.rules.surfaceKey
import java.util.concurrent.TimeUnit

data class SurfaceUi(val key: String, val label: String, val enabled: Boolean)

data class PlatformUi(
    val id: String,
    val label: String,
    val installed: Boolean,
    val blocksToday: Int,
    val foregroundMillisToday: Long?,
    val surfaces: List<SurfaceUi>,
)

data class HomeUiState(
    val serviceEnabled: Boolean,
    val usageAccess: Boolean,
    val platforms: List<PlatformUi>,
    /** Daily short-video allowance; 0 blocks immediately. */
    val allowanceMinutes: Int,
    /** Time watched today within the allowance, never above it. */
    val watchedTodayMs: Long,
)

/** [usage] is null when usage access has not been granted. */
fun buildHomeUiState(
    rules: BlockingRules,
    disabled: Set<String>,
    blocks: Map<String, Int>,
    usage: Map<String, Long>?,
    installed: Set<String>,
    serviceEnabled: Boolean,
    allowanceMinutes: Int,
    watchedMs: Long,
): HomeUiState = HomeUiState(
    serviceEnabled = serviceEnabled,
    allowanceMinutes = allowanceMinutes,
    watchedTodayMs = watchedMs.coerceAtMost(TimeUnit.MINUTES.toMillis(allowanceMinutes.toLong())),
    usageAccess = usage != null,
    platforms = rules.platforms.map { platform ->
        PlatformUi(
            id = platform.id,
            label = platform.label,
            installed = platform.packages.any { it in installed },
            blocksToday = blocks[platform.id] ?: 0,
            foregroundMillisToday = usage?.let { byPackage -> platform.packages.sumOf { byPackage[it] ?: 0L } },
            surfaces = platform.surfaces.map { surface ->
                val key = surfaceKey(platform.id, surface.id)
                SurfaceUi(key, surface.label, key !in disabled)
            },
        )
    },
)
