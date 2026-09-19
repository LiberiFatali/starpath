package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
/**
 * Manages alert triggers (vibrate and screen wake-up) for navigation updates.
 *
 * Prevents unnecessary watch wakes while cruising straight, while guaranteeing
 * the watch wakes up:
 * 1. When a new maneuver instruction begins (e.g. STRAIGHT -> TURN_LEFT).
 * 2. At key distance milestones approaching the turn: 200m -> 100m -> 50m.
 * 3. On route recalculation / rerouting.
 */
class NavAlertManager {
    companion object {
        val MILESTONES = listOf(200, 100, 50)
    }

    enum class AlertReason {
        NONE,
        MANEUVER_CHANGED,
        REROUTING,
        MILESTONE_CROSSED,
    }

    data class Decision(
        val shouldAlert: Boolean,
        val reason: AlertReason,
    )

    var lastAlertedManeuver: NavManeuver? = null
        private set
    var lastMilestone: Int? = null
        private set

    fun evaluate(update: NavUpdate): Decision {
        if (update.state == NavState.REROUTING) {
            recordAlert(update.maneuver.canonical(), null)
            return Decision(shouldAlert = true, AlertReason.REROUTING)
        }

        // Maneuver changed (canonical left/right/straight/?): always alert.
        // Left variants (slight/sharp/keep) share one display mark, so they
        // must not re-alert among themselves.
        if (update.maneuver.canonical() != lastAlertedManeuver) {
            val milestone = calculateMilestone(update.distanceMeters)
            recordAlert(update.maneuver.canonical(), milestone)
            return Decision(shouldAlert = true, AlertReason.MANEUVER_CHANGED)
        }

        // For straight riding, don't alert on milestones
        if (update.maneuver.canonical() == NavManeuver.STRAIGHT) {
            return Decision(shouldAlert = false, AlertReason.NONE)
        }

        val dist = update.distanceMeters
        if (dist != null) {
            val milestone = calculateMilestone(dist)
            // If we crossed into a tighter milestone (e.g., entered <= 200m, or <= 100m)
            if (milestone != null && (lastMilestone == null || milestone < lastMilestone!!)) {
                recordAlert(update.maneuver.canonical(), milestone)
                return Decision(shouldAlert = true, AlertReason.MILESTONE_CROSSED)
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

    private fun recordAlert(maneuver: NavManeuver, milestone: Int?) {
        lastAlertedManeuver = maneuver
        lastMilestone = milestone
    }

    fun reset() {
        lastAlertedManeuver = null
        lastMilestone = null
    }
}
