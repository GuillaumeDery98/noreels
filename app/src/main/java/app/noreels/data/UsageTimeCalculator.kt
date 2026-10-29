package app.noreels.data

enum class UsageEventType { RESUMED, PAUSED }

data class UsageEventSample(val packageName: String, val timestamp: Long, val type: UsageEventType)

object UsageTimeCalculator {

    /**
     * Foreground time per package between [dayStart] and [now], from chronologically ordered events.
     * A PAUSED with no prior RESUMED means the app was already open at [dayStart]; a trailing
     * RESUMED means it is still open at [now].
     */
    fun foregroundMillis(
        events: List<UsageEventSample>,
        dayStart: Long,
        now: Long,
        packages: Set<String>,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val openSince = mutableMapOf<String, Long>()
        val seen = mutableSetOf<String>()
        for (event in events) {
            if (event.packageName !in packages) continue
            val firstEvent = seen.add(event.packageName)
            when (event.type) {
                UsageEventType.RESUMED -> openSince.putIfAbsent(event.packageName, event.timestamp)
                UsageEventType.PAUSED -> {
                    val start = openSince.remove(event.packageName) ?: if (firstEvent) dayStart else continue
                    totals.merge(event.packageName, event.timestamp - start, Long::plus)
                }
            }
        }
        openSince.forEach { (pkg, start) -> totals.merge(pkg, now - start, Long::plus) }
        return totals
    }
}
