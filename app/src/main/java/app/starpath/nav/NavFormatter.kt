package app.starpath.nav

/**
 * Formats a [NavUpdate] into the glanceable card that Zepp forwards to the
 * Active 2 (466×466 round): title must fit without scrolling, with large prominent arrows.
 */
object NavFormatter {

    /** e.g. "◀◀ 200 m" / "▲▲ 1.2 km" / "🏁" */
    fun title(update: NavUpdate): String {
        if (update.state == NavState.REROUTING) return "… Rerouting"
        val glyph = update.maneuver.glyph
        return if (update.distanceText.isBlank()) glyph
        else "$glyph ${update.distanceText}"
    }

    /** Street only, max ~24 chars so it never scrolls while riding. */
    fun text(update: NavUpdate): String {
        val s = update.street.trim()
        return if (s.length <= 26) s else s.take(25).trimEnd() + "…"
    }

    /** ETA / remaining line, passed through, capped. */
    fun subText(update: NavUpdate): String =
        update.tripLine.trim().take(48)

    /**
     * True when the card content changed enough to re-post. Re-posts drive
     * the watch vibration, so distance-only flapping within the same bucket
     * still re-posts (fresh ETA), but identical content never does.
     */
    fun shouldRepost(old: Card?, update: NavUpdate): Boolean {
        if (old == null) return true
        val card = toCard(update)
        return old != card
    }

    fun toCard(update: NavUpdate): Card =
        Card(title(update), text(update), subText(update), update.state, update.maneuver)

    data class Card(
        val title: String,
        val text: String,
        val sub: String,
        val state: NavState,
        val maneuver: NavManeuver = NavManeuver.UNKNOWN,
    )
}
