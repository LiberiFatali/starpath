package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
/**
 * Formats a [NavUpdate] into the glanceable card that Zepp forwards to the
 * watch (e.g. Amazfit Active 2, 466×466 round): title must fit without
 * scrolling, with large prominent arrows.
 *
 * Display model is 5-state: `◀◀ / ▶▶ / ▲▲ / DEST / ?` (or `<- / -> / ^ / DEST / ?` in
 * ASCII mode). Parser detail (slight/sharp/keep/U-turn)
 * collapses here via [NavManeuver.canonical] — never a fake arrow.
 *
 * Title leads with the next-turn countdown, arrow last: `260 m ▶▶`.
 * Sub carries the destination remaining trip, prefixed so the two distances
 * never blur: `DEST 450 m · 6 min` (ETA clock dropped) — never the raw Maps
 * header verbatim. The final arrival card (title `DEST`) and rerouting carry
 * no prefix.
 */
object NavFormatter {

    const val PREFS_FILE = "starpath_prefs"
    const val PREF_ASCII_ARROWS = "ascii_arrows"

    /**
     * Universal destination tag on the trip line. All-caps to match the
     * `DEST` arrival mark, ASCII-safe for every Zepp watch, no translation
     * needed (like ETA/GPS).
     */
    const val DEST_PREFIX = "DEST "

    /** e.g. "200 m ◀◀" / "1.2 km ▲▲" / "DEST" / "200 m ?" (ASCII: "200 m <-").
     * Arrival is mark-only: near arrival Maps reuses the remaining-trip
     * distance in the street line, so "$dist DEST" would echo line 2/3. */
    fun title(update: NavUpdate, useAscii: Boolean = false): String {
        if (update.state == NavState.REROUTING) return "… Rerouting"
        val mark = update.maneuver.displayMark(useAscii)
        if (update.maneuver == NavManeuver.DESTINATION) return mark
        val dist = update.distanceText.trim()
        return if (dist.isBlank()) mark
        else "$dist $mark"
    }

    /** Street only, max ~24 chars so it never scrolls while riding. */
    fun text(update: NavUpdate): String {
        val s = update.street.trim()
        return if (s.length <= 26) s else s.take(25).trimEnd() + "…"
    }

    private val tripDistance = Regex("""([\d.,]+\s*(?:km|m|mi|ft|yd))\b(?!/h)""")
    private val tripDuration = Regex("""(?i)(\d+\s*(?:mins?|minutes?|phút|giờ|hrs?|hours?))\b""")

    /**
     * Compacts the Maps trip header to remaining distance + duration, e.g.
     * "6 min · 450 m · 22:12 ETA" → "450 m · 6 min". The ETA clock is
     * deliberately dropped. Returns "" when neither parses (a bare street
     * fallback or ETA-only string carries nothing the watch needs).
     */
    fun compactTrip(tripLine: String): String {
        val t = tripLine.trim()
        if (t.isBlank()) return ""
        val dist = tripDistance.find(t)?.groupValues?.get(1)?.trim().orEmpty()
        val dur = tripDuration.find(t)?.groupValues?.get(1)?.trim().orEmpty()
        return listOf(dist, dur).filter { it.isNotBlank() }.joinToString(" · ").take(48)
    }

    /** Destination remaining line, e.g. `DEST 450 m · 6 min`, total capped at 48 chars. */
    fun subText(update: NavUpdate): String {
        val compact = compactTrip(update.tripLine)
        if (compact.isBlank()) return ""
        // Final arrival already says DEST in the title; rerouting has no trip.
        if (update.state == NavState.REROUTING) return compact
        if (update.maneuver == NavManeuver.DESTINATION) return compact
        return (DEST_PREFIX + compact).take(48)
    }

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
