package app.noreels.rules

import kotlinx.serialization.json.Json
import java.util.regex.PatternSyntaxException

object RulesParser {

    private val json = Json { ignoreUnknownKeys = false }

    fun parse(text: String): BlockingRules {
        val rules = json.decodeFromString<BlockingRules>(text)
        validate(rules)
        return rules
    }

    private fun validate(rules: BlockingRules) {
        val keys = mutableSetOf<String>()
        for (platform in rules.platforms) {
            require(platform.packages.isNotEmpty()) { "Platform ${platform.id} has no package" }
            for (surface in platform.surfaces) {
                val key = surfaceKey(platform.id, surface.id)
                require(keys.add(key)) { "Duplicate surface key $key" }
                when (surface.type) {
                    SurfaceType.NODES -> {
                        require(surface.nodes.isNotEmpty()) { "Surface $key has no node matcher" }
                        surface.nodes.forEach { validateMatcher(key, it) }
                    }
                    SurfaceType.URL ->
                        require(surface.urlPatterns.isNotEmpty()) { "Surface $key has no URL pattern" }
                    SurfaceType.WHOLE_APP -> Unit
                }
            }
        }
    }

    private fun validateMatcher(key: String, matcher: NodeMatcher) {
        require(
            matcher.viewId != null || matcher.textRegex != null || matcher.contentDescriptionRegex != null
        ) { "Surface $key has a matcher without criterion" }
        listOfNotNull(matcher.textRegex, matcher.contentDescriptionRegex).forEach { pattern ->
            try {
                Regex(pattern)
            } catch (e: PatternSyntaxException) {
                throw IllegalArgumentException("Surface $key has an invalid regex: $pattern", e)
            }
        }
    }
}
