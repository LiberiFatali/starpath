package app.starpath.nav

/**
 * Manages alert triggers (vibrate and screen wake-up) for navigation updates.
 *
 * Prevents unnecessary watch wakes while cruising straight, while guaranteeing
 * the watch wakes up:
 * 1. When a new maneuver instruction begins (e.g. STRAIGHT -> TURN_LEFT).
 * 2. At key distance milestones approaching the turn:
 *    500m -> 200m -> 100m -> 50m.
 * 3. On route recalculation / rerouting.
 * 4. Stay-awake pulse: every [pulseIntervalMs] when approaching a turn
 *    (<= 300m) to keep the notification card visible on screen while waiting at
 *    intersections or moving slowly.
 */
class NavAlertManager(
    val pulseIntervalMs: Long = DEFAULT_PULSE_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_PULSE_INTERVAL_MS = 18_000L // 18 seconds
        val MILESTONES = listOf(500, 200, 100, 50)
    }

    enum class AlertReason {
        NONE,
        MANEUVER_CHANGED,
        REROUTING,
        MILESTONE_CROSSED,
        STAY_AWAKE_PULSE,
    }

    data class Decision(
        val shouldAlert: Boolean,
        val reason: AlertReason,
    )

    var lastAlertedManeuver: NavManeuver? = null
        private set
    var lastMilestone: Int? = null
        private set
    var lastAlertTimeMs: Long = 0L
        private set

    fun evaluate(
        update: NavUpdate,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): Decision {
        if (update.state == NavState.REROUTING) {
            recordAlert(update.maneuver.canonical(), null, currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.REROUTING)
        }

        // Maneuver changed (canonical left/right/straight/?): always alert.
        // Left variants (slight/sharp/keep) share one display mark, so they
        // must not re-alert among themselves.
        if (update.maneuver.canonical() != lastAlertedManeuver) {
            val milestone = calculateMilestone(update.distanceMeters)
            recordAlert(update.maneuver.canonical(), milestone, currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.MANEUVER_CHANGED)
        }

        // For straight riding, don't alert on milestones or pulse
        if (update.maneuver.canonical() == NavManeuver.STRAIGHT) {
            return Decision(shouldAlert = false, AlertReason.NONE)
        }

        val dist = update.distanceMeters
        if (dist != null) {
            val milestone = calculateMilestone(dist)
            // If we crossed into a tighter milestone (e.g., entered <= 500m, or <= 200m)
            if (milestone != null && (lastMilestone == null || milestone < lastMilestone!!)) {
                recordAlert(update.maneuver.canonical(), milestone, currentTimeMs)
                return Decision(shouldAlert = true, AlertReason.MILESTONE_CROSSED)
            }

            // Stay-awake pulse within 300m of turn if watch screen likely timed out
            if (dist <= 300 && (currentTimeMs - lastAlertTimeMs) >= pulseIntervalMs) {
                recordAlert(update.maneuver.canonical(), milestone ?: lastMilestone, currentTimeMs)
                return Decision(shouldAlert = true, AlertReason.STAY_AWAKE_PULSE)
            }
        }

        return Decision(shouldAlert = false, AlertReason.NONE)
    }

    /**
     * Determines which milestone threshold the current distance falls under.
     * Returns the smallest milestone that the distance is <= to.
     */
    fun calculateMilestone(distanceMeters: Int?): Int? {
        if (distanceMeters == null) return null
        return MILESTONES.lastOrNull { distanceMeters <= it }
    }

    private fun recordAlert(maneuver: NavManeuver, milestone: Int?, timeMs: Long) {
        lastAlertedManeuver = maneuver
        lastMilestone = milestone
        lastAlertTimeMs = timeMs
    }

    fun reset() {
        lastAlertedManeuver = null
        lastMilestone = null
        lastAlertTimeMs = 0L
    }
}
