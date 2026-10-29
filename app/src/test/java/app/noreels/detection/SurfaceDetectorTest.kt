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
    fun braveShortsUrlDetected() {
        val root = FakeNode(
            children = listOf(FakeNode(viewId = BRAVE_URL_BAR, text = "youtube.com/shorts/abc", bounds = ScreenRect(150, 2200, 900, 2320)))
        )

        val detection = detector.detect(BRAVE, root, screen, allEnabled)

        assertThat(detection).isEqualTo(Detection("youtube", "shorts_web", BlockAction.BACK))
    }

    @Test
    fun chromeRegularYoutubeUrlIgnored() {
        assertThat(detector.detect(CHROME, chromeWithUrl("youtube.com/watch?v=x"), screen, allEnabled)).isNull()
    }

    @Test
    fun viewIdMatchersUseIdLookupWithoutWalkingTree() {
        val root = LookupOnlyNode(mapOf(IG_CLIPS to listOf(FakeNode(viewId = IG_CLIPS, bounds = screen))))

        val detection = detector.detect(INSTAGRAM, root, screen, allEnabled)

        assertThat(detection).isEqualTo(Detection("instagram", "reels", BlockAction.BACK))
    }

    @Test
    fun chromeUrlBarFoundByIdLookupWithoutWalkingTree() {
        val urlBar = FakeNode(viewId = CHROME_URL_BAR, text = "youtube.com/shorts/abc")
        val root = LookupOnlyNode(mapOf(CHROME_URL_BAR to listOf(urlBar)))

        assertThat(detector.detect(CHROME, root, screen, allEnabled))
            .isEqualTo(Detection("youtube", "shorts_web", BlockAction.BACK))
    }

    @Test
    fun chromeSearchMentioningTiktokIgnored() {
        assertThat(detector.detect(CHROME, chromeWithUrl("google.com/search?q=tiktok.com"), screen, allEnabled)).isNull()
    }

    @Test
    fun chromeFocusedUrlBarIgnored() {
        val root = FakeNode(
            children = listOf(FakeNode(viewId = CHROME_URL_BAR, text = "youtube.com/shorts/abc", isFocused = true))
        )

        assertThat(detector.detect(CHROME, root, screen, allEnabled)).isNull()
    }

    @Test
    fun chromeFullUrlWithSubdomainDetected() {
        assertThat(detector.detect(CHROME, chromeWithUrl("https://m.youtube.com/shorts/abc"), screen, allEnabled))
            .isEqualTo(Detection("youtube", "shorts_web", BlockAction.BACK))
    }

    @Test
    fun chromeTiktokSubdomainDetected() {
        assertThat(detector.detect(CHROME, chromeWithUrl("www.tiktok.com/@someone"), screen, allEnabled))
            .isEqualTo(Detection("tiktok", "web", BlockAction.BACK))
    }

    @Test
    fun unknownPackageReturnsNull() {
        val root = FakeNode(children = listOf(FakeNode(viewId = IG_CLIPS, bounds = screen)))

        assertThat(detector.detect("com.example.other", root, screen, allEnabled)).isNull()
    }

    private fun chromeWithUrl(url: String) = FakeNode(
        children = listOf(FakeNode(viewId = CHROME_URL_BAR, text = url, bounds = ScreenRect(150, 100, 900, 220)))
    )

    private data class FakeNode(
        override val viewId: String? = null,
        override val text: CharSequence? = null,
        override val contentDescription: CharSequence? = null,
        override val bounds: ScreenRect = ScreenRect(0, 0, 1080, 2400),
        override val isVisibleToUser: Boolean = true,
        override val isSelected: Boolean = false,
        override val isFocused: Boolean = false,
        override val children: List<ScreenNode> = emptyList(),
    ) : ScreenNode

    /** A root whose subtree must never be walked: only the id lookup is allowed. */
    private class LookupOnlyNode(private val byId: Map<String, List<ScreenNode>>) : ScreenNode {
        override val viewId: String? = null
        override val text: CharSequence? = null
        override val contentDescription: CharSequence? = null
        override val bounds = ScreenRect(0, 0, 1080, 2400)
        override val isVisibleToUser = true
        override val isSelected = false
        override val isFocused = false
        override val children: List<ScreenNode> get() = error("subtree walked")
        override fun findByViewId(viewId: String): List<ScreenNode> = byId[viewId].orEmpty()
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val FACEBOOK = "com.facebook.katana"
        const val CHROME = "com.android.chrome"
        const val CHROME_URL_BAR = "com.android.chrome:id/url_bar"
        const val BRAVE = "com.brave.browser"
        const val BRAVE_URL_BAR = "com.brave.browser:id/url_bar"
        const val IG_CLIPS = "com.instagram.android:id/clips_viewer_container"
    }
}
