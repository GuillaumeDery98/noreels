package app.noreels.allowance

import java.time.LocalDate

/** What the service must do next for the daily allowance. */
sealed interface AllowanceStep {
    /** A surface is on screen with time left: let it play and tick again after [delayMs]. */
    data class Watch(val delayMs: Long) : AllowanceStep

    /** The foreground is unknown for now: check again after [delayMs] without counting. */
    data class Probe(val delayMs: Long) : AllowanceStep

    /** No surface any more: cancel the tick and persist what was watched. */
    data object Stop : AllowanceStep

    /** A surface is on screen and the allowance is used up: run the usual block. */
    data object Block : AllowanceStep

    /** Run a full surface detection of [packageName]. */
    data class Evaluate(val packageName: String) : AllowanceStep

    /** Nothing to do. */
    data object Idle : AllowanceStep
}

/**
 * Decides, without touching Android, how the service counts watched time and when it re-checks.
 *
 * The self-tick only compares the foreground package (re-running detection every second would be
 * too costly); leaving a surface inside an app is reported by accessibility events instead. A
 * foreground read as null is a transient, not a departure: it is re-checked for up to
 * [probeWindowMs] before counting stops. After the screen comes back on, the last evaluated app is
 * only evaluated once it is really in front.
 */
class AllowanceController(
    private val meter: AllowanceMeter,
    private val tickMs: Long,
    private val probeWindowMs: Long,
) {
    var allowanceMs = 0L

    private var watchedPackage: String? = null
    private var unknownSince: Long? = null
    private var resumingPackage: String? = null
    private var resumeSince = 0L

    fun onDetection(now: Long, day: LocalDate, packageName: String, interactive: Boolean): AllowanceStep {
        resumingPackage = null
        unknownSince = null
        meter.observe(now, day, watching = interactive)
        val remainingMs = meter.remainingMs(day, allowanceMs)
        if (remainingMs <= 0) {
            watchedPackage = null
            return AllowanceStep.Block
        }
        watchedPackage = packageName
        return AllowanceStep.Watch(minOf(tickMs, remainingMs))
    }

    fun onNoDetection(now: Long, day: LocalDate): AllowanceStep {
        meter.observe(now, day, watching = false)
        watchedPackage = null
        unknownSince = null
        resumingPackage = null
        return AllowanceStep.Stop
    }

    /** The screen could not be read; retry while a surface is being watched. */
    fun onEvaluationFailed(): AllowanceStep =
        if (watchedPackage != null) AllowanceStep.Probe(tickMs) else AllowanceStep.Idle

    fun onScreenOn(now: Long, lastPackage: String?): AllowanceStep {
        if (lastPackage == null) return AllowanceStep.Idle
        resumingPackage = lastPackage
        resumeSince = now
        return AllowanceStep.Probe(0)
    }

    fun onTick(now: Long, day: LocalDate, interactive: Boolean, foregroundPackage: String?): AllowanceStep {
        watchedPackage?.let { return watchingTick(now, day, it, interactive, foregroundPackage) }
        resumingPackage?.let { return resumingTick(now, it, foregroundPackage) }
        return AllowanceStep.Idle
    }

    private fun watchingTick(
        now: Long,
        day: LocalDate,
        packageName: String,
        interactive: Boolean,
        foregroundPackage: String?,
    ): AllowanceStep {
        if (interactive && foregroundPackage == null) {
            meter.observe(now, day, watching = false)
            val since = unknownSince ?: now.also { unknownSince = it }
            return if (now - since < probeWindowMs) AllowanceStep.Probe(tickMs) else onNoDetection(now, day)
        }
        if (!interactive || foregroundPackage != packageName) return onNoDetection(now, day)
        unknownSince = null
        meter.observe(now, day, watching = true)
        val remainingMs = meter.remainingMs(day, allowanceMs)
        return if (remainingMs <= 0) AllowanceStep.Evaluate(packageName) else AllowanceStep.Watch(minOf(tickMs, remainingMs))
    }

    private fun resumingTick(now: Long, packageName: String, foregroundPackage: String?): AllowanceStep {
        if (foregroundPackage == packageName) {
            resumingPackage = null
            return AllowanceStep.Evaluate(packageName)
        }
        if (now - resumeSince < probeWindowMs) return AllowanceStep.Probe(tickMs)
        resumingPackage = null
        return AllowanceStep.Idle
    }
}
