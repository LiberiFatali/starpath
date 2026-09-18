package app.starpath.nav

/**
 * Navigation-phase validity gate: decides whether a parsed Maps update is a
 * real navigation state worth forwarding to the phone card / watch.
 *
 * Pure Kotlin (no Android dependency) so it stays JVM-unit-testable —
 * [StarPathListener] is Android-bound, this is not.
 *
 * Drops the shape of non-navigation Maps pushes (e.g. the "Has it closed? /
 * Should this place be shown as closed" crowdsource prompt): ENROUTE +
 * UNKNOWN maneuver + no distance + no icon verdict.
 */
object NavGate {

    /**
     * @param appliedIcon true when [IconClassifier] overrode an UNKNOWN text
     *   verdict (icon-only turn instruction — a real navigation state whose
     *   maneuver is already copied onto [NavUpdate.maneuver]).
     */
    fun shouldForward(update: NavUpdate, appliedIcon: Boolean): Boolean {
        if (update.state == NavState.REROUTING) return true
        if (appliedIcon) return true
        if (update.maneuver != NavManeuver.UNKNOWN) return true
        return update.distanceMeters != null
    }
}
