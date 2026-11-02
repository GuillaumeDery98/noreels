package app.noreels.ui

private val DIGITS = Regex("\\d+")

/**
 * Turns the hours and minutes typed in the allowance dialog into total minutes.
 * A blank field counts as 0; anything else must be digits within 0..23 hours and 0..59 minutes.
 * Returns null when the input is invalid.
 */
fun parseAllowanceMinutes(hours: String, minutes: String): Int? {
    val h = parseField(hours, max = 23) ?: return null
    val m = parseField(minutes, max = 59) ?: return null
    return h * 60 + m
}

private fun parseField(text: String, max: Int): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    if (!DIGITS.matches(trimmed)) return null
    return trimmed.toIntOrNull()?.takeIf { it in 0..max }
}
