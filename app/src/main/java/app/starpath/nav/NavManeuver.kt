package app.starpath.nav

/**
 * Maneuver kind. The watch displays essential directions at a glance on
 * smartwatches (e.g. Amazfit Active 2): left, right, straight, destination —
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
    STRAIGHT("▲▲", "^"),
    KEEP_LEFT("◀", "<-"),
    KEEP_RIGHT("▶", "->"),
    DESTINATION("DEST", "DEST"),
    UNKNOWN("?", "?"),
    ;

    /**
     * Collapses to the essential display model: left, right, straight,
     * destination, else UNKNOWN (`?`). Left/right families (slight/sharp/keep)
     * map to the plain turn; sideless maneuvers (U-turn) map
     * to UNKNOWN since the notification text carries no side — never a fake
     * arrow. DESTINATION stays distinct so both pin mirrors collapse to one
     * `DEST` text and dedup posts arrival once.
     */
    fun canonical(): NavManeuver = when (this) {
        TURN_LEFT, SLIGHT_LEFT, SHARP_LEFT, KEEP_LEFT -> TURN_LEFT
        TURN_RIGHT, SLIGHT_RIGHT, SHARP_RIGHT, KEEP_RIGHT -> TURN_RIGHT
        STRAIGHT -> STRAIGHT
        DESTINATION -> DESTINATION
        else -> UNKNOWN
    }

    /** Display mark for the watch: glyph (`◀◀`) or ASCII (`<-`) for the canonical maneuver. */
    fun displayMark(useAscii: Boolean): String = when (canonical()) {
        TURN_LEFT -> if (useAscii) "<-" else "◀◀"
        TURN_RIGHT -> if (useAscii) "->" else "▶▶"
        STRAIGHT -> if (useAscii) "^" else "▲▲"
        DESTINATION -> "DEST"
        else -> "?"
    }
}
