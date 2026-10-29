package app.noreels.detection

import app.noreels.rules.BlockingRules
import app.noreels.rules.RulesParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SurfaceDetectorTest {

    private val rules: BlockingRules =
        RulesParser.parse(File("src/main/res/raw/blocking_rules.json").readText())
    private val detector = SurfaceDetector(rules)
    private val screen = ScreenRect(0, 0, 1080, 2400)
    private val allEnabled: (String) -> Boolean = { true }

    @Test
    fun instagramReelsFixtureDetected() {
        val root = DumpsysFixture.load("fixtures/instagram_reels.txt", INSTAGRAM)

        val detection = detector.detect(INSTAGRAM, root, screen, allEnabled)

        assertThat(detection).isEqualTo(Detection("instagram", "reels", BlockAction.BACK))
    }

    @Test
    fun instagramHomeFixtureNotDetected() {
        val root = DumpsysFixture.load("fixtures/instagram_home.txt", INSTAGRAM)

        assertThat(detector.detect(INSTAGRAM, root, screen, allEnabled)).isNull()
    }

    @Test
    fun nullRootReturnsNull() {
        assertThat(detector.detect(INSTAGRAM, null, screen, allEnabled)).isNull()
    }

    @Test
    fun invisibleNodeIgnored() {
        val root = FakeNode(children = listOf(FakeNode(viewId = IG_CLIPS, bounds = screen, isVisibleToUser = false)))

        assertThat(detector.detect(INSTAGRAM, root, screen, allEnabled)).isNull()
    }

    @Test
    fun smallNodeIgnored() {
        val thumbnail = ScreenRect(0, 0, 540, 900)
        val root = FakeNode(children = listOf(FakeNode(viewId = IG_CLIPS, bounds = thumbnail)))

        assertThat(detector.detect(INSTAGRAM, root, screen, allEnabled)).isNull()
    }

    @Test
    fun disabledSurfaceIsIgnored() {
        val root = FakeNode(children = listOf(FakeNode(viewId = IG_CLIPS, bounds = screen)))

        val detection = detector.detect(INSTAGRAM, root, screen) { it != "instagram.reels" }

        assertThat(detection).isNull()
    }

    @Test
    fun tiktokWholeAppReturnsHome() {
        val detection = detector.detect("com.zhiliaoapp.musically", null, screen, allEnabled)

        assertThat(detection).isEqualTo(Detection("tiktok", "app", BlockAction.HOME))
    }

    @Test
    fun facebookSelectedReelsTabDetected() {
        val tab = FakeNode(contentDescription = "Reels, onglet 2 sur 6", isSelected = true, bounds = ScreenRect(180, 200, 360, 320))
        val root = FakeNode(children = listOf(tab))

        val detection = detector.detect(FACEBOOK, root, screen, allEnabled)

        assertThat(detection).isEqualTo(Detection("facebook", "reels", BlockAction.BACK))
    }

    @Test
    fun facebookUnselectedReelsTabIgnored() {
        val tab = FakeNode(contentDescription = "Reels, onglet 2 sur 6", isSelected = false, bounds = ScreenRect(180, 200, 360, 320))
        val root = FakeNode(children = listOf(tab))

        assertThat(detector.detect(FACEBOOK, root, screen, allEnabled)).isNull()
    }

    @Test
    fun chromeShortsUrlDetected() {
        val root = chromeWithUrl("youtube.com/shorts/abc")

        val detection = detector.detect(CHROME, root, screen, allEnabled)

        assertThat(detection).isEqualTo(Detection("youtube", "shorts_web", BlockAction.BACK))
    }

    @Test
    fun chromeRegularYoutubeUrlIgnored() {
        assertThat(detector.detect(CHROME, chromeWithUrl("youtube.com/watch?v=x"), screen, allEnabled)).isNull()
    }

    @Test
    fun unknownPackageReturnsNull() {
        val root = FakeNode(children = listOf(FakeNode(viewId = IG_CLIPS, bounds = screen)))

        assertThat(detector.detect("com.example.other", root, screen, allEnabled)).isNull()
    }

    private fun chromeWithUrl(url: String) = FakeNode(
        children = listOf(FakeNode(viewId = "com.android.chrome:id/url_bar", text = url, bounds = ScreenRect(150, 100, 900, 220)))
    )

    private data class FakeNode(
        override val viewId: String? = null,
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val bounds: ScreenRect = ScreenRect(0, 0, 1080, 2400),
        override val isVisibleToUser: Boolean = true,
        override val isSelected: Boolean = false,
        override val children: List<ScreenNode> = emptyList(),
    ) : ScreenNode

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val FACEBOOK = "com.facebook.katana"
        const val CHROME = "com.android.chrome"
        const val IG_CLIPS = "com.instagram.android:id/clips_viewer_container"
    }
}
