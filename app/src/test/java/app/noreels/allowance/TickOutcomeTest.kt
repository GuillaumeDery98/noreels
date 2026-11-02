package app.noreels.allowance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TickOutcomeTest {

    @Test
    fun samePackageWithTimeLeftContinues() {
        assertThat(tickOutcome(interactive = true, foregroundPackage = YOUTUBE, watchedPackage = YOUTUBE, remainingMs = 5_000))
            .isEqualTo(TickOutcome.CONTINUE)
    }

    @Test
    fun screenOffStops() {
        assertThat(tickOutcome(interactive = false, foregroundPackage = YOUTUBE, watchedPackage = YOUTUBE, remainingMs = 5_000))
            .isEqualTo(TickOutcome.STOP)
    }

    @Test
    fun otherForegroundPackageStops() {
        assertThat(tickOutcome(interactive = true, foregroundPackage = LAUNCHER, watchedPackage = YOUTUBE, remainingMs = 5_000))
            .isEqualTo(TickOutcome.STOP)
    }

    @Test
    fun nullRootStops() {
        assertThat(tickOutcome(interactive = true, foregroundPackage = null, watchedPackage = YOUTUBE, remainingMs = 5_000))
            .isEqualTo(TickOutcome.STOP)
    }

    @Test
    fun exhaustedWhileStillThereEnforces() {
        assertThat(tickOutcome(interactive = true, foregroundPackage = YOUTUBE, watchedPackage = YOUTUBE, remainingMs = 0))
            .isEqualTo(TickOutcome.ENFORCE)
    }

    @Test
    fun exhaustedButGoneStops() {
        assertThat(tickOutcome(interactive = true, foregroundPackage = LAUNCHER, watchedPackage = YOUTUBE, remainingMs = 0))
            .isEqualTo(TickOutcome.STOP)
    }

    @Test
    fun stillWatchingRequiresLitScreenAndSamePackage() {
        assertThat(stillWatching(interactive = true, foregroundPackage = YOUTUBE, watchedPackage = YOUTUBE)).isTrue()
        assertThat(stillWatching(interactive = false, foregroundPackage = YOUTUBE, watchedPackage = YOUTUBE)).isFalse()
        assertThat(stillWatching(interactive = true, foregroundPackage = LAUNCHER, watchedPackage = YOUTUBE)).isFalse()
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
    }
}
