package app.noreels.detection

import app.noreels.rules.surfaceKey

data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Read-only view of one node of the foreground app's screen, independent of the Android framework. */
interface ScreenNode {
    val viewId: String?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val bounds: ScreenRect
    val isVisibleToUser: Boolean
    val isSelected: Boolean
    val isFocused: Boolean
    val children: List<ScreenNode>

    /**
     * Nodes of this subtree with the given resource id. The default walks the tree; the
     * accessibility adapter overrides it with a single lookup done inside the target app.
     */
    fun findByViewId(viewId: String): List<ScreenNode> {
        val found = mutableListOf<ScreenNode>()
        val queue = ArrayDeque<ScreenNode>().apply { add(this@ScreenNode) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.viewId == viewId) found += node
            queue.addAll(node.children)
        }
        return found
    }
}

enum class BlockAction { BACK, HOME }

data class Detection(val platformId: String, val surfaceId: String, val action: BlockAction) {
    val key: String get() = surfaceKey(platformId, surfaceId)
}
