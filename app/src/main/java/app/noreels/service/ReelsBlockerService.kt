package app.noreels.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.noreels.NoReelsApplication
import app.noreels.R
import app.noreels.detection.BlockAction
import app.noreels.detection.BlockDecider
import app.noreels.detection.Detection
import app.noreels.detection.ScreenRect
import app.noreels.detection.SurfaceDetector
import app.noreels.rules.watchedPackages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches the official social apps and leaves blocked surfaces with a global BACK (or HOME).
 * It never acts on the apps' own nodes.
 */
class ReelsBlockerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as NoReelsApplication).container }
    private val detector by lazy { SurfaceDetector(container.rules) }
    private val decider by lazy {
        BlockDecider(container.rules.debounceMs, container.rules.escalationWindowMs) { SystemClock.uptimeMillis() }
    }
    private val lastEvaluation = mutableMapOf<String, Long>()

    @Volatile
    private var disabledSurfaces: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply { packageNames = container.rules.watchedPackages().toTypedArray() }
        scope.launch { container.settings.disabledSurfaces.collect { disabledSurfaces = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName || !shouldEvaluate(event, eventPackage)) return

        val detection = try {
            val root = rootInActiveWindow
            val foregroundPackage = root?.packageName?.toString() ?: eventPackage
            detector.detect(foregroundPackage, root?.let(::AccessibilityScreenNode), screenBounds()) {
                it !in disabledSurfaces
            }
        } catch (e: IllegalStateException) {
            // Nodes can be recycled by the system while we walk them; the next event retries.
            return
        }
        if (detection == null) {
            decider.onNoDetection()
            return
        }

        val action = decider.decide(detection) ?: return
        perform(detection, action)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun shouldEvaluate(event: AccessibilityEvent, eventPackage: String): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return true
        val now = SystemClock.uptimeMillis()
        val last = lastEvaluation[eventPackage]
        if (last != null && now - last < container.rules.contentEvaluationIntervalMs) return false
        lastEvaluation[eventPackage] = now
        return true
    }

    private fun perform(detection: Detection, action: BlockAction) {
        val globalAction = when (action) {
            BlockAction.BACK -> GLOBAL_ACTION_BACK
            BlockAction.HOME -> GLOBAL_ACTION_HOME
        }
        if (!performGlobalAction(globalAction)) return
        Toast.makeText(this, R.string.blocked_toast, Toast.LENGTH_SHORT).show()
        if (action == detection.action) {
            scope.launch { container.stats.recordBlock(detection.platformId) }
        }
    }

    private fun screenBounds(): ScreenRect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            return ScreenRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        val metrics = resources.displayMetrics
        return ScreenRect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
}
