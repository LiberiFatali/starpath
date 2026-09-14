package app.starpath.nav

/**
 * Maneuver kind. The arrow glyph is designed to be large, prominent, and readable
 * at a glance on smartwatches (e.g. Amazfit Active 2).
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
    UNKNOWN("•", "-"),
}
