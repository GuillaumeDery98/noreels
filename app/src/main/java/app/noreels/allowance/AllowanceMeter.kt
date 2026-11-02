package app.noreels.allowance

import java.time.LocalDate

/** Watched time of [day] as it should be persisted. */
data class WatchedTotal(val day: LocalDate, val totalMs: Long)

/**
 * Accumulates the time spent on blocked surfaces for the current day.
 *
 * Time only counts between two consecutive observations that both saw a surface on a lit screen,
 * and each interval is capped at [maxGapMs] so a period the service did not see never counts.
 * `now` values come from a monotonic clock; the day comes from the local calendar.
 */
class AllowanceMeter(
    private val maxGapMs: Long,
    private val flushIntervalMs: Long,
) {
    private var day: LocalDate? = null
    private var watchedMs = 0L
    private var lastWatchingAt: Long? = null
    private var lastFlushAt: Long? = null
    private var unsaved = false

    /** Seeds the total stored for [day]; time already measured for that day is kept on top. */
    fun restore(day: LocalDate, watchedMs: Long) {
        this.watchedMs = watchedMs + if (this.day == day) this.watchedMs else 0L
        this.day = day
    }

    fun observe(now: Long, day: LocalDate, watching: Boolean) {
        if (lastFlushAt == null) lastFlushAt = now
        if (this.day != day) {
            this.day = day
            watchedMs = 0L
        }
        val last = lastWatchingAt
        if (watching && last != null) {
            val elapsed = (now - last).coerceIn(0L, maxGapMs)
            if (elapsed > 0) {
                watchedMs += elapsed
                unsaved = true
            }
        }
        lastWatchingAt = if (watching) now else null
    }

    fun watchedMs(day: LocalDate): Long = if (this.day == day) watchedMs else 0L

    fun remainingMs(day: LocalDate, allowanceMs: Long): Long = (allowanceMs - watchedMs(day)).coerceAtLeast(0L)

    /**
     * Returns the total to persist when there is unsaved time and either [force] is set or
     * the flush interval has elapsed since the last flush; null otherwise.
     */
    fun takeFlush(now: Long, force: Boolean): WatchedTotal? {
        val currentDay = day ?: return null
        if (!unsaved) return null
        val last = lastFlushAt
        if (!force && last != null && now - last < flushIntervalMs) return null
        lastFlushAt = now
        unsaved = false
        return WatchedTotal(currentDay, watchedMs)
    }
}
