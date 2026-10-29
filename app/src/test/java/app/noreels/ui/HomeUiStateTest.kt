package app.noreels.ui

import app.noreels.rules.BlockingRules
import app.noreels.rules.RulesParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class HomeUiStateTest {

    private val rules: BlockingRules =
        RulesParser.parse(File("src/main/res/raw/blocking_rules.json").readText())
    private val allInstalled = rules.platforms.flatMap { it.packages }.toSet()

    private fun build(
        disabled: Set<String> = emptySet(),
        blocks: Map<String, Int> = emptyMap(),
        usage: Map<String, Long>? = emptyMap(),
        installed: Set<String> = allInstalled,
    ) = buildHomeUiState(rules, disabled, blocks, usage, installed, serviceEnabled = true)

    @Test
    fun buildsPlatformsInRulesOrder() {
        assertThat(build().platforms.map { it.id })
            .containsExactly("instagram", "youtube", "facebook", "snapchat", "tiktok")
            .inOrder()
    }

    @Test
    fun disabledSurfaceShownOff() {
        val instagram = build(disabled = setOf("instagram.reels")).platforms.first { it.id == "instagram" }

        assertThat(instagram.surfaces.first { it.key == "instagram.reels" }.enabled).isFalse()
        assertThat(instagram.surfaces.first { it.key == "instagram.reels_web" }.enabled).isTrue()
    }

    @Test
    fun usageNullWithoutAccess() {
        val state = build(usage = null)

        assertThat(state.usageAccess).isFalse()
        assertThat(state.platforms.map { it.foregroundMillisToday }.toSet()).containsExactly(null)
    }

    @Test
    fun tiktokUsageSumsBothPackages() {
        val usage = mapOf("com.zhiliaoapp.musically" to 60_000L, "com.ss.android.ugc.trill" to 30_000L)

        val tiktok = build(usage = usage).platforms.first { it.id == "tiktok" }

        assertThat(tiktok.foregroundMillisToday).isEqualTo(90_000L)
    }

    @Test
    fun notInstalledFlagged() {
        val state = build(installed = setOf("com.instagram.android"), blocks = mapOf("instagram" to 3))

        assertThat(state.platforms.first { it.id == "instagram" }.installed).isTrue()
        assertThat(state.platforms.first { it.id == "instagram" }.blocksToday).isEqualTo(3)
        assertThat(state.platforms.first { it.id == "snapchat" }.installed).isFalse()
    }
}
