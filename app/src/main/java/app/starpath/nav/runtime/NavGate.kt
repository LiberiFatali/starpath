package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
/**
 * Navigation-phase validity gate: decides whether a parsed Maps update is a
 * real navigation state worth forwarding to the phone card / watch.
 *
 * Pure Kotlin (no Android dependency) so it stays JVM-unit-testable —
 * [StarPathListener] is Android-bound, this is not.
 *
 * Drops the shape of non-navigation Maps pushes (e.g. the "Has it closed? /
 * Should this place be shown as closed" crowdsource prompt): ENROUTE +
 * UNKNOWN maneuver + no icon verdict. Text-only UNKNOWN is never navigation —
 * a real instruction carries either an icon verdict or a maneuver verb.
 */
object NavGate {

    /**
     * @param appliedIcon true when [IconClassifier] decided the final
     *   maneuver (icon-first; text is the fallback for icon-missing or
     *   low-confidence frames).
     */
    fun shouldForward(update: NavUpdate, appliedIcon: Boolean): Boolean {
        if (update.state == NavState.REROUTING) return true
        if (appliedIcon) return true
        return update.maneuver != NavManeuver.UNKNOWN
    }

    /**
     * Rerouting is transient, never latched: any live ENROUTE instruction
     * with a street clears a stale REROUTING card, even when weak
     * (UNKNOWN with no icon verdict).
     * Without this the watch freezes on "… Rerouting" after Maps has
     * already recovered (field: no_text_rerouting).
     */
    fun shouldForward(update: NavUpdate, appliedIcon: Boolean, lastPosted: NavUpdate?): Boolean {
        if (lastPosted != null &&
            lastPosted.state == NavState.REROUTING &&
            update.state == NavState.ENROUTE &&
            update.street.isNotBlank()
        ) {
            return true
        }
        return shouldForward(update, appliedIcon)
    }
}
