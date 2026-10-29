package app.noreels.detection

import app.noreels.rules.BlockingRules
import app.noreels.rules.NodeMatcher
import app.noreels.rules.SurfaceType
import app.noreels.rules.surfaceKey

class SurfaceDetector(rules: BlockingRules) {

    private val maxTraversedNodes = rules.maxTraversedNodes

    private val surfacesByPackage: Map<String, List<CompiledSurface>> = buildMap {
        for (platform in rules.platforms) {
            val surfaces = platform.surfaces
                .filter { it.type != SurfaceType.URL }
                .map { CompiledSurface(platform.id, it.id, it.type, it.nodes.map(::CompiledMatcher), emptyList()) }
            platform.packages.forEach { put(it, surfaces) }
        }
    }

    private val urlSurfaces: List<CompiledSurface> = rules.platforms.flatMap { platform ->
        platform.surfaces
            .filter { it.type == SurfaceType.URL }
            .map { CompiledSurface(platform.id, it.id, it.type, emptyList(), it.urlPatterns.map(String::lowercase)) }
    }

    private val urlBarIdsByBrowser: Map<String, Set<String>> =
        rules.browsers.associate { it.packageName to it.urlBarIds.toSet() }

    fun detect(packageName: String, root: ScreenNode?, screen: ScreenRect, isEnabled: (String) -> Boolean): Detection? {
        surfacesByPackage[packageName]?.let { surfaces ->
            return detectInApp(surfaces.filter { isEnabled(it.key) }, root, screen)
        }
        urlBarIdsByBrowser[packageName]?.let { urlBarIds ->
            return detectInBrowser(urlSurfaces.filter { isEnabled(it.key) }, urlBarIds, root)
        }
        return null
    }

    private fun detectInApp(surfaces: List<CompiledSurface>, root: ScreenNode?, screen: ScreenRect): Detection? {
        surfaces.firstOrNull { it.type == SurfaceType.WHOLE_APP }?.let { return it.toDetection(BlockAction.HOME) }
        val nodeSurfaces = surfaces.filter { it.type == SurfaceType.NODES }
        if (root == null || nodeSurfaces.isEmpty()) return null
        var found: CompiledSurface? = null
        traverse(root) { node ->
            found = nodeSurfaces.firstOrNull { surface -> surface.matchers.any { it.matches(node, screen) } }
            found != null
        }
        return found?.toDetection(BlockAction.BACK)
    }

    private fun detectInBrowser(surfaces: List<CompiledSurface>, urlBarIds: Set<String>, root: ScreenNode?): Detection? {
        if (root == null || surfaces.isEmpty()) return null
        var url: String? = null
        traverse(root) { node ->
            if (node.isVisibleToUser && node.viewId in urlBarIds) url = node.text?.toString()?.lowercase()
            url != null
        }
        val current = url ?: return null
        return surfaces.firstOrNull { surface -> surface.urlPatterns.any { current.contains(it) } }
            ?.toDetection(BlockAction.BACK)
    }

    /** Breadth-first walk that stops when [visit] returns true or the node budget is spent. */
    private fun traverse(root: ScreenNode, visit: (ScreenNode) -> Boolean) {
        val queue = ArrayDeque<ScreenNode>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < maxTraversedNodes) {
            val node = queue.removeFirst()
            visited++
            if (visit(node)) return
            queue.addAll(node.children)
        }
    }

    private class CompiledSurface(
        val platformId: String,
        val surfaceId: String,
        val type: SurfaceType,
        val matchers: List<CompiledMatcher>,
        val urlPatterns: List<String>,
    ) {
        val key = surfaceKey(platformId, surfaceId)
        fun toDetection(action: BlockAction) = Detection(platformId, surfaceId, action)
    }

    private class CompiledMatcher(private val matcher: NodeMatcher) {
        private val textRegex = matcher.textRegex?.let(::Regex)
        private val contentDescriptionRegex = matcher.contentDescriptionRegex?.let(::Regex)

        fun matches(node: ScreenNode, screen: ScreenRect): Boolean {
            if (!node.isVisibleToUser) return false
            if (matcher.viewId != null && node.viewId != matcher.viewId) return false
            if (textRegex != null && !textRegex.containsMatchIn(node.text ?: return false)) return false
            if (contentDescriptionRegex != null &&
                !contentDescriptionRegex.containsMatchIn(node.contentDescription ?: return false)
            ) return false
            if (matcher.selected != null && node.isSelected != matcher.selected) return false
            return node.bounds.width >= screen.width * matcher.minWidthRatio &&
                node.bounds.height >= screen.height * matcher.minHeightRatio
        }
    }
}
