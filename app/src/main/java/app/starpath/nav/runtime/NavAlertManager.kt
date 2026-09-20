package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
/**
 * Manages alert triggers (vibrate and screen wake-up) for navigation updates.
 *
 * Next-turn distance cannot be extracted reliably from the Maps notification,
 * so all distance-based reminding (milestones, proximity pulses) was removed.
 * The watch wakes up:
 * 1. When a new maneuver instruction begins (e.g. STRAIGHT -> TURN_LEFT).
 * 2. On route recalculation / rerouting.
 * 3. Stale-instruction reminder: when the same instruction (canonical
 *    direction + street + state) is unchanged for [staleIntervalMs], re-alert
 *    so the rider gets a reminder of the upcoming turn while approaching it.
 *    Repeats every [staleIntervalMs] until the instruction changes. Applies to
 *    all maneuvers, including straight cruising.
 */
class NavAlertManager(
    val staleIntervalMs: Long = DEFAULT_STALE_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_STALE_INTERVAL_MS = 30_000L // 30 seconds
    }

    enum class AlertReason {
        NONE,
        MANEUVER_CHANGED,
        REROUTING,
        STALE_REMINDER,
    }

    data class Decision(
        val shouldAlert: Boolean,
        val reason: AlertReason,
    )

    var lastAlertedManeuver: NavManeuver? = null
        private set
    var lastAlertTimeMs: Long = 0L
        private set

    fun evaluate(
        update: NavUpdate,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): Decision {
        if (update.state == NavState.REROUTING) {
            recordAlert(update.maneuver.canonical(), currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.REROUTING)
        }

        // Maneuver changed (canonical left/right/straight/?): always alert.
        // Left variants (slight/sharp/keep) share one display mark, so they
        // must not re-alert among themselves.
        if (update.maneuver.canonical() != lastAlertedManeuver) {
            recordAlert(update.maneuver.canonical(), currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.MANEUVER_CHANGED)
        }

        // Same instruction for a while: remind the rider of the upcoming turn.
        // Pure wall-clock check — no distance needed. Applies to every
        // maneuver, including straight cruising.
        if ((currentTimeMs - lastAlertTimeMs) >= staleIntervalMs) {
            recordAlert(update.maneuver.canonical(), currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.STALE_REMINDER)
        }

        return Decision(shouldAlert = false, AlertReason.NONE)
    }

    private fun recordAlert(maneuver: NavManeuver, timeMs: Long) {
        lastAlertedManeuver = maneuver
        lastAlertTimeMs = timeMs
    }

    fun reset() {
        lastAlertedManeuver = null
        lastAlertTimeMs = 0L
    }
}
