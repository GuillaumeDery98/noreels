package app.noreels.allowance

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class AllowanceControllerTest {

    private val day = LocalDate.of(2026, 11, 2)
    private val meter = AllowanceMeter(maxGapMs = 2_000, flushIntervalMs = 15_000)
    private val controller = AllowanceController(meter, tickMs = 1_000, probeWindowMs = 3_000).apply {
        allowanceMs = 60_000
    }

    @Test
    fun detectionWithTimeLeftWatches() {
        assertThat(controller.onDetection(0, day, TIKTOK, interactive = true)).isEqualTo(AllowanceStep.Watch(1_000))
    }

    @Test
    fun detectionNearTheEndTicksAtTheRemainingTime() {
        meter.restore(day, 59_600)

        assertThat(controller.onDetection(0, day, TIKTOK, interactive = true)).isEqualTo(AllowanceStep.Watch(400))
    }

    @Test
    fun zeroAllowanceBlocks() {
        controller.allowanceMs = 0

        assertThat(controller.onDetection(0, day, TIKTOK, interactive = true)).isEqualTo(AllowanceStep.Block)
    }

    @Test
    fun tickOnSameAppKeepsWatchingAndCounts() {
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onTick(1_000, day, interactive = true, foregroundPackage = TIKTOK)).isEqualTo(AllowanceStep.Watch(1_000))
        assertThat(meter.watchedMs(day)).isEqualTo(1_000)
    }

    @Test
    fun tickWhenAllowanceRunsOutEvaluates() {
        meter.restore(day, 59_000)
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onTick(1_000, day, interactive = true, foregroundPackage = TIKTOK)).isEqualTo(AllowanceStep.Evaluate(TIKTOK))
    }

    @Test
    fun tickWithScreenOffStops() {
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onTick(1_000, day, interactive = false, foregroundPackage = TIKTOK)).isEqualTo(AllowanceStep.Stop)
        assertThat(meter.watchedMs(day)).isEqualTo(0)
    }

    @Test
    fun tickOnAnotherAppStops() {
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onTick(1_000, day, interactive = true, foregroundPackage = LAUNCHER)).isEqualTo(AllowanceStep.Stop)
    }

    @Test
    fun unknownForegroundProbesWithoutCountingWithinTheWindow() {
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onTick(1_000, day, interactive = true, foregroundPackage = null)).isEqualTo(AllowanceStep.Probe(1_000))
        assertThat(controller.onTick(2_000, day, interactive = true, foregroundPackage = TIKTOK)).isEqualTo(AllowanceStep.Watch(1_000))
        assertThat(meter.watchedMs(day)).isEqualTo(0)
    }

    @Test
    fun unknownForegroundStopsAfterTheWindow() {
        controller.onDetection(0, day, TIKTOK, interactive = true)
        controller.onTick(1_000, day, interactive = true, foregroundPackage = null)

        assertThat(controller.onTick(4_000, day, interactive = true, foregroundPackage = null)).isEqualTo(AllowanceStep.Stop)
    }

    @Test
    fun failedEvaluationWhileWatchingRetries() {
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onEvaluationFailed()).isEqualTo(AllowanceStep.Probe(1_000))
    }

    @Test
    fun failedEvaluationWhenIdleDoesNothing() {
        assertThat(controller.onEvaluationFailed()).isEqualTo(AllowanceStep.Idle)
    }

    @Test
    fun noDetectionStopsAndLaterTicksAreIdle() {
        controller.onDetection(0, day, TIKTOK, interactive = true)

        assertThat(controller.onNoDetection(500, day)).isEqualTo(AllowanceStep.Stop)
        assertThat(controller.onTick(1_000, day, interactive = true, foregroundPackage = TIKTOK)).isEqualTo(AllowanceStep.Idle)
    }

    @Test
    fun screenOnWaitsForTheAppToBeInFrontBeforeEvaluating() {
        assertThat(controller.onScreenOn(0, TIKTOK)).isEqualTo(AllowanceStep.Probe(0))
        assertThat(controller.onTick(0, day, interactive = true, foregroundPackage = KEYGUARD)).isEqualTo(AllowanceStep.Probe(1_000))
        assertThat(controller.onTick(1_000, day, interactive = true, foregroundPackage = TIKTOK)).isEqualTo(AllowanceStep.Evaluate(TIKTOK))
    }

    @Test
    fun screenOnNeverEvaluatesAnAppThatIsNotInFront() {
        controller.onScreenOn(0, TIKTOK)
        controller.onTick(0, day, interactive = true, foregroundPackage = null)
        controller.onTick(1_000, day, interactive = true, foregroundPackage = LAUNCHER)

        assertThat(controller.onTick(3_000, day, interactive = true, foregroundPackage = LAUNCHER)).isEqualTo(AllowanceStep.Idle)
    }

    @Test
    fun screenOnWithoutKnownAppIsIdle() {
        assertThat(controller.onScreenOn(0, null)).isEqualTo(AllowanceStep.Idle)
    }

    private companion object {
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val KEYGUARD = "com.android.systemui"
    }
}
