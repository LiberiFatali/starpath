package app.starpath.nav

/**
 * Maneuver kind. The watch displays only three essential directions at a
 * glance on smartwatches (e.g. Amazfit Active 2): left, right, straight —
 * everything else renders as `?` (see [canonical]).
 */
enum class NavManeuver(val glyph: String, val ascii: String) {
    TURN_LEFT("◀◀", "<-"),
    TURN_RIGHT("▶▶", "->"),
    SLIGHT_LEFT("↖↖", "<-"),
    SLIGHT_RIGHT("↗↗", "->"),
    SHARP_LEFT("⮠⮠", "<-"),
    SHARP_RIGHT("⮡⮡", "->"),
    UTURN("↩↩", "U"),
    ROUNDABOUT("🔄", "O"),
    STRAIGHT("▲▲", "^"),
    KEEP_LEFT("◀", "<-"),
    KEEP_RIGHT("▶", "->"),
    EXIT("↗", "exit"),
    DESTINATION("🏁", "[]"),
    UNKNOWN("?", "?"),
    ;

    /**
     * Collapses to the essential display model: left, right, straight, else
     * UNKNOWN (`?`). Left/right families (slight/sharp/keep) map to the plain
     * turn; sideless maneuvers (U-turn, roundabout, exit, destination) map to
     * UNKNOWN since the notification text carries no side — never a fake arrow.
     */
    fun canonical(): NavManeuver = when (this) {
        TURN_LEFT, SLIGHT_LEFT, SHARP_LEFT, KEEP_LEFT -> TURN_LEFT
        TURN_RIGHT, SLIGHT_RIGHT, SHARP_RIGHT, KEEP_RIGHT -> TURN_RIGHT
        STRAIGHT -> STRAIGHT
        else -> UNKNOWN
    }

    /** Display mark for the watch: glyph (`◀◀`) or ASCII (`<-`) for the canonical maneuver. */
    fun displayMark(useAscii: Boolean): String = when (canonical()) {
        TURN_LEFT -> if (useAscii) "<-" else "◀◀"
        TURN_RIGHT -> if (useAscii) "->" else "▶▶"
        STRAIGHT -> if (useAscii) "^" else "▲▲"
        else -> "?"
    }
}
