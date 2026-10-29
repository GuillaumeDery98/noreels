package app.noreels.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Instant
import java.time.ZoneId

class UsageTimeRepository(
    private val context: Context,
    private val clock: () -> Long,
) {

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun todayForegroundMillis(packages: Set<String>): Map<String, Long> {
        val now = clock()
        val dayStart = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val usageStats = context.getSystemService(UsageStatsManager::class.java)
        val events = usageStats.queryEvents(dayStart, now)
        val samples = mutableListOf<UsageEventSample>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.PAUSED
                else -> continue
            }
            samples += UsageEventSample(event.packageName, event.timeStamp, type)
        }
        return UsageTimeCalculator.foregroundMillis(samples, dayStart, now, packages)
    }
}
