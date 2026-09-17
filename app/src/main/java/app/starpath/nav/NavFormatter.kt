package app.starpath.nav

/**
 * Formats a [NavUpdate] into the glanceable card that Zepp forwards to the
 * watch (e.g. Amazfit Active 2, 466×466 round): title must fit without
 * scrolling, with large prominent arrows.
 *
 * Display model is 5-state: `◀◀ / ▶▶ / ▲▲ / DEST / ?` (or `<- / -> / ^ / DEST / ?` in
 * ASCII mode). Parser detail (slight/sharp/keep/U-turn)
 * collapses here via [NavManeuver.canonical] — never a fake arrow.
 */
object NavFormatter {

    const val PREFS_FILE = "starpath_prefs"
    const val PREF_ASCII_ARROWS = "ascii_arrows"

    /** e.g. "◀◀ 200 m" / "▲▲ 1.2 km" / "DEST" / "? 200 m" (ASCII: "<- 200 m") */
    fun title(update: NavUpdate, useAscii: Boolean = false): String {
        if (update.state == NavState.REROUTING) return "… Rerouting"
        val mark = update.maneuver.displayMark(useAscii)
        return if (update.distanceText.isBlank()) mark
        else "$mark ${update.distanceText}"
    }

    /** Street only, max ~24 chars so it never scrolls while riding. */
    fun text(update: NavUpdate): String {
        val s = update.street.trim()
        return if (s.length <= 26) s else s.take(25).trimEnd() + "…"
    }

    /** ETA / remaining line, passed through, capped. */
    fun subText(update: NavUpdate): String =
        update.tripLine.trim().take(48)

    fun toCard(update: NavUpdate, useAscii: Boolean = false): Card =
        Card(title(update, useAscii), text(update), subText(update), update.state, update.maneuver)

    data class Card(
        val title: String,
        val text: String,
        val sub: String,
        val state: NavState,
        val maneuver: NavManeuver = NavManeuver.UNKNOWN,
    )
}
