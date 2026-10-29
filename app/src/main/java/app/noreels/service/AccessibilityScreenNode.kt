package app.noreels.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.noreels.detection.ScreenNode
import app.noreels.detection.ScreenRect

/** Adapts an [AccessibilityNodeInfo] to the framework-free [ScreenNode] the detector reads. */
class AccessibilityScreenNode(private val info: AccessibilityNodeInfo) : ScreenNode {

    override val viewId: String? get() = info.viewIdResourceName
    override val text: CharSequence? get() = info.text
    override val contentDescription: CharSequence? get() = info.contentDescription
    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val isSelected: Boolean get() = info.isSelected

    override val bounds: ScreenRect by lazy {
        val rect = Rect()
        info.getBoundsInScreen(rect)
        ScreenRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    override val children: List<ScreenNode> by lazy {
        (0 until info.childCount).mapNotNull { index -> info.getChild(index)?.let(::AccessibilityScreenNode) }
    }
}
