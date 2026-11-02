package app.noreels.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AllowanceInputTest {

    @Test
    fun parsesHoursAndMinutes() {
        assertThat(parseAllowanceMinutes("1", "30")).isEqualTo(90)
        assertThat(parseAllowanceMinutes(" 0 ", "45")).isEqualTo(45)
    }

    @Test
    fun blankMeansZero() {
        assertThat(parseAllowanceMinutes("", " ")).isEqualTo(0)
        assertThat(parseAllowanceMinutes("2", "")).isEqualTo(120)
    }

    @Test
    fun rejectsOutOfRange() {
        assertThat(parseAllowanceMinutes("24", "0")).isNull()
        assertThat(parseAllowanceMinutes("0", "60")).isNull()
    }

    @Test
    fun rejectsNonDigits() {
        assertThat(parseAllowanceMinutes("1a", "0")).isNull()
        assertThat(parseAllowanceMinutes("-1", "0")).isNull()
        assertThat(parseAllowanceMinutes("0", "1.5")).isNull()
    }
}
