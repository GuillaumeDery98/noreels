package app.noreels.detection

/**
 * Turns repeated detections into actions: ignores bursts inside [debounceMs] and escalates a BACK
 * to HOME when the same surface is still on screen within [escalationWindowMs].
 */
class BlockDecider(
    private val debounceMs: Long,
    private val escalationWindowMs: Long,
    private val clock: () -> Long,
) {
    private data class LastAction(val at: Long, val action: BlockAction, val leftSurface: Boolean = false)

    private val lastActions = mutableMapOf<String, LastAction>()

    fun decide(detection: Detection): BlockAction? {
        val now = clock()
        val last = lastActions[detection.key]
        val action = when {
            last != null && now - last.at < debounceMs -> return null
            last != null && !last.leftSurface && last.action == BlockAction.BACK && now - last.at < escalationWindowMs ->
                BlockAction.HOME
            else -> detection.action
        }
        lastActions[detection.key] = LastAction(now, action)
        return action
    }

    /**
     * The screen was evaluated and no blocked surface is shown: the previous BACK worked, so a
     * later detection is a new visit, not a failed exit to escalate. The debounce still applies,
     * because a surface animating out can be seen again for a moment.
     */
    fun onNoDetection() {
        lastActions.replaceAll { _, last -> last.copy(leftSurface = true) }
    }
}
