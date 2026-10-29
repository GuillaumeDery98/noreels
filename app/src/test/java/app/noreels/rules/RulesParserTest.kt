package app.noreels.rules

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class RulesParserTest {

    private fun shippedRules(): BlockingRules =
        RulesParser.parse(File("src/main/res/raw/blocking_rules.json").readText())

    @Test
    fun parsesShippedRules() {
        val rules = shippedRules()

        assertThat(rules.platforms.map { it.id })
            .containsExactly("instagram", "youtube", "facebook", "snapchat", "tiktok")
            .inOrder()
        val chrome = rules.browsers.single { it.packageName == "com.android.chrome" }
        assertThat(chrome.urlBarIds).contains("com.android.chrome:id/url_bar")
        assertThat(rules.watchedPackages()).containsAtLeast(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.android.chrome",
        )
    }

    @Test
    fun rejectsInvalidRegex() {
        val json = rulesJson(
            """{"id":"reels","label":"Reels","type":"nodes","nodes":[{"textRegex":"(unclosed"}]}"""
        )
        assertThrows(IllegalArgumentException::class.java) { RulesParser.parse(json) }
    }

    @Test
    fun rejectsMatcherWithoutCriterion() {
        val json = rulesJson(
            """{"id":"reels","label":"Reels","type":"nodes","nodes":[{"minWidthRatio":0.8}]}"""
        )
        assertThrows(IllegalArgumentException::class.java) { RulesParser.parse(json) }
    }

    @Test
    fun rejectsDuplicateSurfaceKey() {
        val surface = """{"id":"reels","label":"Reels","type":"nodes","nodes":[{"viewId":"x:id/y"}]}"""
        val json = rulesJson("$surface,$surface")
        assertThrows(IllegalArgumentException::class.java) { RulesParser.parse(json) }
    }

    private fun rulesJson(surfaces: String) = """
        {
          "debounceMs": 700,
          "escalationWindowMs": 2500,
          "contentEvaluationIntervalMs": 300,
          "maxTraversedNodes": 3000,
          "platforms": [
            {"id": "test", "label": "Test", "packages": ["com.test"], "surfaces": [$surfaces]}
          ],
          "browsers": []
        }
    """.trimIndent()
}
