package app.noreels.allowance

/** What the service does on a self-tick while a surface is watched within the allowance. */
enum class TickOutcome {
    /** Still on the surface with time left: count and tick again. */
    CONTINUE,

    /** Screen off or the watched app left the foreground: stop counting and ticking. */
    STOP,

    /** Still on the surface and the allowance is used up: run a full evaluation to block. */
    ENFORCE,
}

/**
 * The tick does not re-run surface detection (too costly every second on apps matched by regex);
 * leaving a surface inside the app is reported by accessibility events instead.
 */
fun stillWatching(interactive: Boolean, foregroundPackage: String?, watchedPackage: String): Boolean =
    interactive && foregroundPackage == watchedPackage

fun tickOutcome(
    interactive: Boolean,
    foregroundPackage: String?,
    watchedPackage: String,
    remainingMs: Long,
): TickOutcome = when {
    !stillWatching(interactive, foregroundPackage, watchedPackage) -> TickOutcome.STOP
    remainingMs <= 0 -> TickOutcome.ENFORCE
    else -> TickOutcome.CONTINUE
}
