package app.noreels.detection

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Builds a [ScreenNode] tree from `adb shell uiautomator dump`, which reads the same accessibility
 * tree as the service. Used for apps whose view ids are obfuscated, where rules rely on
 * content descriptions. The dump only lists nodes visible to the user.
 */
object UiAutomatorFixture {

    private val boundsPattern = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

    fun load(resource: String): ScreenNode {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resource)) { "Missing $resource" }
        val document = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val hierarchy = document.documentElement
        return XmlNode(
            viewId = null,
            text = null,
            contentDescription = null,
            bounds = ScreenRect(0, 0, 0, 0),
            isSelected = false,
            isFocused = false,
            children = childNodes(hierarchy),
        )
    }

    private fun childNodes(element: Element): List<ScreenNode> {
        val nodes = element.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { it.tagName == "node" }
            .map(::toNode)
    }

    private fun toNode(element: Element): ScreenNode {
        val (l, t, r, b) = checkNotNull(boundsPattern.find(element.getAttribute("bounds"))).destructured
        return XmlNode(
            viewId = element.getAttribute("resource-id").ifEmpty { null },
            text = element.getAttribute("text").ifEmpty { null },
            contentDescription = element.getAttribute("content-desc").ifEmpty { null },
            bounds = ScreenRect(l.toInt(), t.toInt(), r.toInt(), b.toInt()),
            isSelected = element.getAttribute("selected") == "true",
            isFocused = element.getAttribute("focused") == "true",
            children = childNodes(element),
        )
    }

    private data class XmlNode(
        override val viewId: String?,
        override val text: CharSequence?,
        override val contentDescription: CharSequence?,
        override val bounds: ScreenRect,
        override val isSelected: Boolean,
        override val isFocused: Boolean,
        override val children: List<ScreenNode>,
    ) : ScreenNode {
        override val isVisibleToUser: Boolean = true
    }
}
