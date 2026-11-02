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
        assertThat(rules.allowanceTickMs).isEqualTo(1000)
        assertThat(rules.allowanceMaxGapMs).isEqualTo(2000)
        assertThat(rules.allowanceFlushMs).isEqualTo(15000)
        assertThat(rules.allowanceProbeWindowMs).isEqualTo(3000)
        val chrome = rules.browsers.single { it.packageName == "com.android.chrome" }
        assertThat(chrome.urlBarIds).contains("com.android.chrome:id/url_bar")
        val brave = rules.browsers.single { it.packageName == "com.brave.browser" }
        assertThat(brave.urlBarIds).contains("com.brave.browser:id/url_bar")
        assertThat(rules.watchedPackages()).containsAtLeast(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.android.chrome",
            "com.brave.browser",
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

    @Test
    fun rejectsMaxGapShorterThanTick() {
        val json = rulesJson(
            """{"id":"reels","label":"Reels","type":"nodes","nodes":[{"viewId":"x:id/y"}]}""",
            allowanceMaxGapMs = 500,
        )
        assertThrows(IllegalArgumentException::class.java) { RulesParser.parse(json) }
    }

    private fun rulesJson(surfaces: String, allowanceMaxGapMs: Long = 2000) = """
        {
          "debounceMs": 700,
          "escalationWindowMs": 2500,
          "contentEvaluationIntervalMs": 300,
          "maxTraversedNodes": 3000,
          "allowanceTickMs": 1000,
          "allowanceMaxGapMs": $allowanceMaxGapMs,
          "allowanceFlushMs": 15000,
          "allowanceProbeWindowMs": 3000,
          "platforms": [
            {"id": "test", "label": "Test", "packages": ["com.test"], "surfaces": [$surfaces]}
          ],
          "browsers": []
        }
    """.trimIndent()
}
