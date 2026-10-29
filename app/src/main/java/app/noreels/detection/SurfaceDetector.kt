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

        // Id matchers use one lookup each; only matchers without an id need a walk of the tree.
        nodeSurfaces.firstOrNull { surface ->
            surface.matchers.any { matcher ->
                matcher.viewId != null && root.findByViewId(matcher.viewId).any { matcher.matches(it, screen) }
            }
        }?.let { return it.toDetection(BlockAction.BACK) }

        val walkSurfaces = nodeSurfaces.filter { surface -> surface.matchers.any { it.viewId == null } }
        if (walkSurfaces.isEmpty()) return null
        var found: CompiledSurface? = null
        traverse(root) { node ->
            found = walkSurfaces.firstOrNull { surface ->
                surface.matchers.any { it.viewId == null && it.matches(node, screen) }
            }
            found != null
        }
        return found?.toDetection(BlockAction.BACK)
    }

    private fun detectInBrowser(surfaces: List<CompiledSurface>, urlBarIds: Set<String>, root: ScreenNode?): Detection? {
        if (root == null || surfaces.isEmpty()) return null
        // A focused bar shows what the user is typing, not the page that is open.
        val url = urlBarIds.asSequence()
            .flatMap { root.findByViewId(it) }
            .firstOrNull { it.isVisibleToUser && !it.isFocused && !it.text.isNullOrBlank() }
            ?.text?.let { WebAddress.parse(it.toString()) }
            ?: return null
        return surfaces.firstOrNull { surface -> surface.urlPatterns.any { url.matches(it) } }
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

    /** Host and path of an address bar text such as `https://m.youtube.com/shorts/x` or `youtube.com/shorts/x`. */
    private class WebAddress(val host: String, val path: String) {

        /** [pattern] is `host[/path]`; it matches the host or any of its subdomains, and a path prefix. */
        fun matches(pattern: WebAddress): Boolean =
            (host == pattern.host || host.endsWith(".${pattern.host}")) && path.startsWith(pattern.path)

        fun matches(pattern: String): Boolean = matches(parse(pattern))

        companion object {
            private val scheme = Regex("^[a-z][a-z0-9+.-]*://")

            fun parse(text: String): WebAddress {
                val address = text.trim().lowercase().replace(scheme, "")
                val hostEnd = address.indexOfFirst { it == '/' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: address.length
                return WebAddress(address.substring(0, hostEnd), address.substring(hostEnd))
            }
        }
    }

    private class CompiledMatcher(private val matcher: NodeMatcher) {
        val viewId: String? = matcher.viewId
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
