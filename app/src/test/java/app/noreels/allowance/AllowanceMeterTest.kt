package app.noreels.allowance

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class AllowanceMeterTest {

    private val day = LocalDate.of(2026, 11, 2)
    private val meter = AllowanceMeter(maxGapMs = 2_000, flushIntervalMs = 15_000)

    @Test
    fun firstObservationAddsNothing() {
        meter.observe(0, day, watching = true)

        assertThat(meter.watchedMs(day)).isEqualTo(0)
    }

    @Test
    fun accumulatesBetweenWatchingObservations() {
        meter.observe(0, day, watching = true)
        meter.observe(1_000, day, watching = true)
        meter.observe(2_000, day, watching = true)

        assertThat(meter.watchedMs(day)).isEqualTo(2_000)
    }

    @Test
    fun screenOffDoesNotCount() {
        meter.observe(0, day, watching = true)
        meter.observe(1_000, day, watching = false)
        meter.observe(5_000, day, watching = true)

        assertThat(meter.watchedMs(day)).isEqualTo(0)
    }

    @Test
    fun gapIsCapped() {
        meter.observe(0, day, watching = true)
        meter.observe(10_000, day, watching = true)

        assertThat(meter.watchedMs(day)).isEqualTo(2_000)
    }

    @Test
    fun newDayStartsFromZero() {
        meter.restore(day, 60_000)
        val nextDay = day.plusDays(1)

        meter.observe(0, nextDay, watching = true)
        meter.observe(1_000, nextDay, watching = true)

        assertThat(meter.watchedMs(nextDay)).isEqualTo(1_000)
        assertThat(meter.watchedMs(day)).isEqualTo(0)
    }

    @Test
    fun restoreSeedsToday() {
        meter.restore(day, 60_000)

        assertThat(meter.watchedMs(day)).isEqualTo(60_000)
    }

    @Test
    fun remainingIsClampedAtZero() {
        meter.restore(day, 90_000)

        assertThat(meter.remainingMs(day, allowanceMs = 60_000)).isEqualTo(0)
        assertThat(meter.remainingMs(day, allowanceMs = 120_000)).isEqualTo(30_000)
    }

    @Test
    fun zeroAllowanceHasNothingRemaining() {
        assertThat(meter.remainingMs(day, allowanceMs = 0)).isEqualTo(0)
    }

    @Test
    fun flushWaitsForInterval() {
        meter.observe(0, day, watching = true)
        meter.observe(1_000, day, watching = true)

        assertThat(meter.takeFlush(1_000, force = false)).isNull()
        assertThat(meter.takeFlush(15_000, force = false)).isEqualTo(WatchedTotal(day, 1_000))
        assertThat(meter.takeFlush(15_001, force = false)).isNull()
    }

    @Test
    fun forcedFlushReturnsPendingTotal() {
        meter.observe(0, day, watching = true)
        meter.observe(1_000, day, watching = true)

        assertThat(meter.takeFlush(1_000, force = true)).isEqualTo(WatchedTotal(day, 1_000))
        assertThat(meter.takeFlush(1_001, force = true)).isNull()
    }
}
