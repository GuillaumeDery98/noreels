package app.noreels.detection

/**
 * Builds a [ScreenNode] tree from the "View Hierarchy" section of
 * `adb shell dumpsys activity activity <component>`.
 *
 * Each line is `<indent><class>{<hash> <flags> <private flags> <l,t-r,b> [#id <pkg>:id/<name>]}`.
 * Bounds are relative to the parent, so absolute bounds accumulate offsets. The first flag
 * character is the visibility (V/I/G); a node is visible only if all its ancestors are.
 */
object DumpsysFixture {

    private val linePattern = Regex(
        """^(\s*)\S+\{[0-9a-f]+ (\S)\S* \S+ (-?\d+),(-?\d+)-(-?\d+),(-?\d+)(?: #[0-9a-f]+ ([^\s}]+))?"""
    )

    fun load(resource: String, packageName: String): ScreenNode {
        val text = checkNotNull(javaClass.classLoader?.getResource(resource)) { "Missing $resource" }.readText()
        return parse(text, packageName)
    }

    fun parse(text: String, packageName: String): ScreenNode {
        val stack = ArrayDeque<Pair<Int, MutableNode>>()
        var root: MutableNode? = null
        for (line in text.lines()) {
            val match = linePattern.find(line) ?: continue
            val (indent, visibility, l, t, r, b, rawId) = match.destructured
            val depth = indent.length
            while (stack.isNotEmpty() && stack.last().first >= depth) stack.removeLast()
            val parent = stack.lastOrNull()?.second
            val offsetX = parent?.bounds?.left ?: 0
            val offsetY = parent?.bounds?.top ?: 0
            val node = MutableNode(
                viewId = rawId.takeIf { it.isNotEmpty() }?.replace(Regex("^app:id/"), "$packageName:id/"),
                bounds = ScreenRect(offsetX + l.toInt(), offsetY + t.toInt(), offsetX + r.toInt(), offsetY + b.toInt()),
                isVisibleToUser = visibility == "V" && (parent?.isVisibleToUser ?: true),
            )
            if (parent == null) root = node else parent.mutableChildren += node
            stack.addLast(depth to node)
        }
        return checkNotNull(root) { "No view hierarchy found" }
    }

    private class MutableNode(
        override val viewId: String?,
        override val bounds: ScreenRect,
        override val isVisibleToUser: Boolean,
    ) : ScreenNode {
        val mutableChildren = mutableListOf<ScreenNode>()
        override val text: CharSequence? = null
        override val contentDescription: CharSequence? = null
        override val isSelected: Boolean = false
        override val children: List<ScreenNode> get() = mutableChildren
    }
}
