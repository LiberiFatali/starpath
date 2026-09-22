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
 * 1. When a new instruction begins: the dedup key changed (canonical
 *    direction, normalized street, or state — e.g. STRAIGHT -> TURN_LEFT,
 *    or the same arrow onto a new street). Left variants (slight/sharp/keep)
 *    share one display mark, so they must not re-alert among themselves.
 * 2. On route recalculation / rerouting.
 * 3. Stale-instruction reminder: when the same instruction is unchanged,
 *    re-alert after [destStaleIntervalMs] for DESTINATION (final approach is
 *    last-chance) or [normalStaleIntervalMs] for anything else. Pure
 *    wall-clock check — no distance needed. Repeats every interval until the
 *    instruction changes.
 *
 * Shares its "changed" predicate with [NavDedup.keyOf] (classes stay separate:
 * this tracks the last *alerted* instruction + time for the buzz decision,
 * [NavDedup] compares against the last *posted* card for the re-post
 * decision).
 */
class NavAlertManager(
    val destStaleIntervalMs: Long = DEFAULT_DEST_STALE_INTERVAL_MS,
    val normalStaleIntervalMs: Long = DEFAULT_NORMAL_STALE_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_DEST_STALE_INTERVAL_MS = 30_000L // 30 seconds
        const val DEFAULT_NORMAL_STALE_INTERVAL_MS = 300_000L // 5 minutes
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
    var lastAlertedKey: NavDedup.Key? = null
        private set
    var lastAlertTimeMs: Long = 0L
        private set

    /** Reminder interval for this update: aggressive on final approach, calm otherwise. */
    fun staleIntervalFor(update: NavUpdate): Long =
        if (update.maneuver.canonical() == NavManeuver.DESTINATION) {
            destStaleIntervalMs
        } else {
            normalStaleIntervalMs
        }

    fun evaluate(
        update: NavUpdate,
        currentTimeMs: Long = System.currentTimeMillis(),
    ): Decision {
        if (update.state == NavState.REROUTING) {
            recordAlert(update, currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.REROUTING)
        }

        // Instruction changed (canonical direction, normalized street, or
        // state — shared predicate with NavDedup.keyOf): always alert.
        if (NavDedup.keyOf(update) != lastAlertedKey) {
            recordAlert(update, currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.MANEUVER_CHANGED)
        }

        // Same instruction for a while: remind the rider of the upcoming turn.
        // Pure wall-clock check — no distance needed.
        if ((currentTimeMs - lastAlertTimeMs) >= staleIntervalFor(update)) {
            recordAlert(update, currentTimeMs)
            return Decision(shouldAlert = true, AlertReason.STALE_REMINDER)
        }

        return Decision(shouldAlert = false, AlertReason.NONE)
    }

    private fun recordAlert(update: NavUpdate, timeMs: Long) {
        lastAlertedManeuver = update.maneuver.canonical()
        lastAlertedKey = NavDedup.keyOf(update)
        lastAlertTimeMs = timeMs
    }

    fun reset() {
        lastAlertedManeuver = null
        lastAlertedKey = null
        lastAlertTimeMs = 0L
    }
}
