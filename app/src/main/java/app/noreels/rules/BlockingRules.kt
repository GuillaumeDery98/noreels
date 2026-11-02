package app.noreels.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlockingRules(
    val debounceMs: Long,
    val escalationWindowMs: Long,
    val contentEvaluationIntervalMs: Long,
    val maxTraversedNodes: Int,
    /** While a surface is watched within the daily allowance, the service re-checks itself this often. */
    val allowanceTickMs: Long,
    /** Longest interval between two observations that still counts as watched time. */
    val allowanceMaxGapMs: Long,
    /** Watched time is written to storage at most this often while watching. */
    val allowanceFlushMs: Long,
    val platforms: List<PlatformRule>,
    val browsers: List<BrowserRule>,
)

@Serializable
data class PlatformRule(
    val id: String,
    val label: String,
    val packages: List<String>,
    val surfaces: List<SurfaceRule>,
)

@Serializable
data class SurfaceRule(
    val id: String,
    val label: String,
    val type: SurfaceType,
    val nodes: List<NodeMatcher> = emptyList(),
    val urlPatterns: List<String> = emptyList(),
)

@Serializable
enum class SurfaceType {
    /** A matching visible node is on screen inside the platform's own app. */
    @SerialName("nodes") NODES,

    /** Any screen of the platform's app. */
    @SerialName("wholeApp") WHOLE_APP,

    /** The address bar of a supported browser shows a matching URL. */
    @SerialName("url") URL,
}

@Serializable
data class NodeMatcher(
    val viewId: String? = null,
    val textRegex: String? = null,
    val contentDescriptionRegex: String? = null,
    val selected: Boolean? = null,
    val minWidthRatio: Float = 0f,
    val minHeightRatio: Float = 0f,
)

@Serializable
data class BrowserRule(
    val packageName: String,
    val urlBarIds: List<String>,
)

fun surfaceKey(platformId: String, surfaceId: String): String = "$platformId.$surfaceId"

fun BlockingRules.watchedPackages(): Set<String> =
    platforms.flatMap { it.packages }.toSet() + browsers.map { it.packageName }
