package app.noreels.data

import app.noreels.data.UsageEventType.PAUSED
import app.noreels.data.UsageEventType.RESUMED
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UsageTimeCalculatorTest {

    private val instagram = "com.instagram.android"
    private val packages = setOf(instagram)

    @Test
    fun sumsResumePausePairs() {
        val events = listOf(
            UsageEventSample(instagram, 1_000, RESUMED),
            UsageEventSample(instagram, 4_000, PAUSED),
            UsageEventSample(instagram, 10_000, RESUMED),
            UsageEventSample(instagram, 12_000, PAUSED),
        )

        val result = UsageTimeCalculator.foregroundMillis(events, dayStart = 0, now = 20_000, packages = packages)

        assertThat(result).containsExactly(instagram, 5_000L)
    }

    @Test
    fun sessionSpanningMidnightIsClipped() {
        val events = listOf(UsageEventSample(instagram, 3_000, PAUSED))

        val result = UsageTimeCalculator.foregroundMillis(events, dayStart = 1_000, now = 20_000, packages = packages)

        assertThat(result).containsExactly(instagram, 2_000L)
    }

    @Test
    fun openSessionCountsUntilNow() {
        val events = listOf(UsageEventSample(instagram, 15_000, RESUMED))

        val result = UsageTimeCalculator.foregroundMillis(events, dayStart = 0, now = 20_000, packages = packages)

        assertThat(result).containsExactly(instagram, 5_000L)
    }

    @Test
    fun ignoresOtherPackages() {
        val events = listOf(
            UsageEventSample("com.other", 1_000, RESUMED),
            UsageEventSample("com.other", 9_000, PAUSED),
        )

        val result = UsageTimeCalculator.foregroundMillis(events, dayStart = 0, now = 20_000, packages = packages)

        assertThat(result).isEmpty()
    }
}
