package app.noreels.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import app.noreels.NoReelsApplication
import app.noreels.R
import app.noreels.allowance.AllowanceController
import app.noreels.allowance.AllowanceMeter
import app.noreels.allowance.AllowanceStep
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Watches the official social apps and leaves blocked surfaces with a global BACK (or HOME)
 * once the daily allowance is used up. It never acts on the apps' own nodes.
 */
class ReelsBlockerService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as NoReelsApplication).container }
    private val detector by lazy { SurfaceDetector(container.rules) }
    private val decider by lazy {
        BlockDecider(container.rules.debounceMs, container.rules.escalationWindowMs) { SystemClock.uptimeMillis() }
    }
    private val throttle by lazy {
        EvaluationThrottle(container.rules.contentEvaluationIntervalMs) { SystemClock.uptimeMillis() }
    }
    private val meter by lazy {
        AllowanceMeter(container.rules.allowanceMaxGapMs, container.rules.allowanceFlushMs)
    }
    private val allowance by lazy {
        AllowanceController(meter, container.rules.allowanceTickMs, container.rules.allowanceProbeWindowMs)
    }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { onTick() }

    @Volatile
    private var disabledSurfaces: Set<String> = emptySet()

    /** Last package evaluated, re-checked when the screen comes back on. */
    private var lastEvaluatedPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            apply(allowance.onScreenOn(SystemClock.uptimeMillis(), lastEvaluatedPackage))
        }
    }
    private var screenReceiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply { packageNames = container.rules.watchedPackages().toTypedArray() }
        scope.launch { container.settings.disabledSurfaces.collect { disabledSurfaces = it } }
        scope.launch {
            container.settings.dailyAllowanceMinutes.collect {
                allowance.allowanceMs = TimeUnit.MINUTES.toMillis(it.toLong())
            }
        }
        scope.launch { meter.restore(LocalDate.now(), container.watchTime.watchedToday.first()) }
        val screenEvents = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // System broadcasts still reach a non-exported receiver.
        ContextCompat.registerReceiver(this, screenReceiver, screenEvents, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName) return
        val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        when (val decision = throttle.onEvent(eventPackage, windowChanged)) {
            ThrottleDecision.Now -> evaluate(eventPackage)
            is ThrottleDecision.Later -> handler.postDelayed({
                throttle.onDeferredRun(eventPackage)
                evaluate(eventPackage)
            }, decision.delayMs)
            ThrottleDecision.Skip -> Unit
        }
    }

    private fun evaluate(eventPackage: String) {
        lastEvaluatedPackage = eventPackage
        var foregroundPackage = eventPackage
        val detection = try {
            val root = rootInActiveWindow
            foregroundPackage = root?.packageName?.toString() ?: eventPackage
            detector.detect(foregroundPackage, root?.let(::AccessibilityScreenNode), screenBounds()) {
                it !in disabledSurfaces
            }
        } catch (e: IllegalStateException) {
            // Nodes can be recycled by the system while we walk them; retry while a surface is watched.
            apply(allowance.onEvaluationFailed())
            return
        }
        val now = SystemClock.uptimeMillis()
        if (detection == null) {
            apply(allowance.onNoDetection(now, LocalDate.now()))
            return
        }

        val step = allowance.onDetection(now, LocalDate.now(), foregroundPackage, powerManager.isInteractive)
        apply(step)
        if (step != AllowanceStep.Block) return
        val action = decider.decide(detection) ?: return
        perform(detection, action)
    }

    /** Counts watched time while a video plays without accessibility events, and notices leaving the app. */
    private fun onTick() {
        val foregroundPackage = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: IllegalStateException) {
            null
        }
        apply(allowance.onTick(SystemClock.uptimeMillis(), LocalDate.now(), powerManager.isInteractive, foregroundPackage))
    }

    private fun apply(step: AllowanceStep) {
        when (step) {
            is AllowanceStep.Watch -> {
                scheduleTick(step.delayMs)
                flush(force = false)
            }
            is AllowanceStep.Probe -> scheduleTick(step.delayMs)
            AllowanceStep.Stop -> {
                handler.removeCallbacks(tick)
                flush(force = true)
                decider.onNoDetection()
            }
            AllowanceStep.Block -> {
                handler.removeCallbacks(tick)
                flush(force = true)
            }
            is AllowanceStep.Evaluate -> evaluate(step.packageName)
            AllowanceStep.Idle -> Unit
        }
    }

    private fun scheduleTick(delayMs: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    private fun flush(force: Boolean) {
        val total = meter.takeFlush(SystemClock.uptimeMillis(), force) ?: return
        scope.launch { container.watchTime.record(total) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver)
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
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
