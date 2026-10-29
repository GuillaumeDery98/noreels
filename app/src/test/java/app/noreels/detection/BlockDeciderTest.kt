package app.noreels.detection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlockDeciderTest {

    private var now = 0L
    private val decider = BlockDecider(debounceMs = 700, escalationWindowMs = 2500, clock = { now })
    private val reels = Detection("instagram", "reels", BlockAction.BACK)

    @Test
    fun firstDetectionReturnsAction() {
        assertThat(decider.decide(reels)).isEqualTo(BlockAction.BACK)
    }

    @Test
    fun withinDebounceReturnsNull() {
        decider.decide(reels)
        now = 300

        assertThat(decider.decide(reels)).isNull()
    }

    @Test
    fun repeatAfterDebounceEscalatesToHome() {
        decider.decide(reels)
        now = 1000

        assertThat(decider.decide(reels)).isEqualTo(BlockAction.HOME)
    }

    @Test
    fun afterEscalationWindowReturnsBackAgain() {
        decider.decide(reels)
        now = 5000

        assertThat(decider.decide(reels)).isEqualTo(BlockAction.BACK)
    }

    @Test
    fun differentSurfacesAreIndependent() {
        decider.decide(reels)
        now = 300

        assertThat(decider.decide(Detection("youtube", "shorts", BlockAction.BACK))).isEqualTo(BlockAction.BACK)
    }
}
