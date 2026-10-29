package app.noreels.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EvaluationThrottleTest {

    private var now = 0L
    private val throttle = EvaluationThrottle(intervalMs = 300, clock = { now })

    @Test
    fun leadingEventRunsNow() {
        assertThat(throttle.onEvent(INSTAGRAM, windowChanged = false)).isEqualTo(ThrottleDecision.Now)
    }

    @Test
    fun burstSchedulesOneTrailingRun() {
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 100

        assertThat(throttle.onEvent(INSTAGRAM, windowChanged = false)).isEqualTo(ThrottleDecision.Later(200))
    }

    @Test
    fun extraEventsWhilePendingAreSkipped() {
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 100
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 150

        assertThat(throttle.onEvent(INSTAGRAM, windowChanged = false)).isEqualTo(ThrottleDecision.Skip)
    }

    @Test
    fun deferredRunRestartsTheInterval() {
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 100
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 300
        throttle.onDeferredRun(INSTAGRAM)
        now = 400

        assertThat(throttle.onEvent(INSTAGRAM, windowChanged = false)).isEqualTo(ThrottleDecision.Later(200))
    }

    @Test
    fun windowChangeAlwaysRunsNow() {
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 50

        assertThat(throttle.onEvent(INSTAGRAM, windowChanged = true)).isEqualTo(ThrottleDecision.Now)
    }

    @Test
    fun afterIntervalRunsNowAgain() {
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 300

        assertThat(throttle.onEvent(INSTAGRAM, windowChanged = false)).isEqualTo(ThrottleDecision.Now)
    }

    @Test
    fun packagesAreIndependent() {
        throttle.onEvent(INSTAGRAM, windowChanged = false)
        now = 100

        assertThat(throttle.onEvent("com.android.chrome", windowChanged = false)).isEqualTo(ThrottleDecision.Now)
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
    }
}
