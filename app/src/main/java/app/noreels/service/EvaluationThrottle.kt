package app.noreels.service

sealed interface ThrottleDecision {
    data object Now : ThrottleDecision
    data class Later(val delayMs: Long) : ThrottleDecision
    data object Skip : ThrottleDecision
}

/**
 * Limits screen evaluations to one per [intervalMs] per package, without losing the end of a burst:
 * events inside the interval schedule a single trailing evaluation, so the final screen of a
 * transition is always looked at.
 */
class EvaluationThrottle(
    private val intervalMs: Long,
    private val clock: () -> Long,
) {
    private val lastRun = mutableMapOf<String, Long>()
    private val pending = mutableSetOf<String>()

    fun onEvent(packageName: String, windowChanged: Boolean): ThrottleDecision {
        val now = clock()
        val last = lastRun[packageName]
        if (windowChanged || last == null || now - last >= intervalMs) {
            lastRun[packageName] = now
            return ThrottleDecision.Now
        }
        if (!pending.add(packageName)) return ThrottleDecision.Skip
        return ThrottleDecision.Later(intervalMs - (now - last))
    }

    /** Called when a [ThrottleDecision.Later] evaluation actually runs. */
    fun onDeferredRun(packageName: String) {
        pending.remove(packageName)
        lastRun[packageName] = clock()
    }
}
