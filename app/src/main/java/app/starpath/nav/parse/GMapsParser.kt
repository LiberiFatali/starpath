package app.starpath.nav.parse

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
/**
 * Parses the Google Maps navigation notification into a [NavUpdate].
 *
 * English only: keyword matching covers English instruction phrasing; set the
 * phone locale to English so Maps emits English strings. Street names pass
 * through verbatim (diacritics kept), and the icon classifier is
 * language-free.
 *
 * Pure Kotlin (no Android dependency on the hot path inputs) so it stays
 * unit-testable. Google changes this layout roughly once a year — when
 * parsing breaks, fix HERE and add the captured dump to [GMapsParserTest].
 *
 * Inputs mirror [android.app.Notification.extras]:
 * @param title android.title — usually the maneuver instruction, e.g.
 *   "Turn left in 200 m onto Nguyen Hue" / "Head north on Main St".
 * @param text android.text — usually the street / next-step detail.
 * @param bigText android.bigText — expanded style text, preferred when present.
 * @param textLines android.textLines — inbox-style lines, last resort.
 */
object GMapsParser {

    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private val distanceFirst = Regex(
        """(?i)(?:in|after)\s+([\d.,]+)\s*(km|m|mi|ft|yd)\b"""
    )
    // Guard against speed ("60 km/h"): unit must not be followed by "/h".
    private val distanceAny = Regex("""([\d.,]+)\s*(km|m|mi|ft|yd)\b(?!/h)""")

    fun parse(
        title: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>,
        subText: String? = null,
    ): NavUpdate? {
        val lines = buildList {
            title?.takeIf { it.isNotBlank() }?.let { add(it) }
            bigText?.takeIf { it.isNotBlank() }?.let { add(it) }
            text?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(textLines.filter { it.isNotBlank() })
        }
        if (lines.isEmpty()) return null

        // Rerouting may appear in any line (Maps sometimes keeps the old
        // instruction in title while bigText already says "Rerouting…").
        val rerouteLine = lines.firstOrNull { isRerouting(it) }
        if (rerouteLine != null) {
            return NavUpdate(
                maneuver = NavManeuver.UNKNOWN,
                distanceText = "",
                distanceMeters = null,
                street = rerouteLine,
                tripLine = "",
                state = NavState.REROUTING,
            )
        }

        val head = lines.first()
        val body = lines.getOrNull(1).orEmpty()
        val trip = lines.drop(2).firstOrNull { looksLikeTripLine(it) }.orEmpty()
            .ifBlank {
                // The "Maps • 44 min • 21 km • 20:02 ETA" header line: useful
                // as trip info, but never as maneuver/distance (it carries
                // *remaining* distance, not next-turn distance).
                subText?.takeIf { it.isNotBlank() && looksLikeTripLine(it) }.orEmpty()
            }
        val (distanceText, distanceMeters) = extractDistance(head)
            ?: extractDistance(body)
            ?: lines.drop(2).firstNotNullOfOrNull { extractDistance(it) }
            ?: ("" to null)

        return NavUpdate(
            // Scan every line in priority order: the maneuver verb may live
            // in title while bigText holds the trip summary, or vice versa.
            maneuver = detectManeuver(lines),
            distanceText = distanceText,
            distanceMeters = distanceMeters,
            street = extractStreet(head, body),
            tripLine = trip.ifBlank { body.takeIf { it != head }.orEmpty() },
            state = NavState.ENROUTE,
        )
    }

    private fun isRerouting(s: String): Boolean {
        val t = s.lowercase()
        return "rerouting" in t || "recalculating" in t ||
            "finding" in t && "route" in t || "searching" in t && "route" in t
    }

    private fun looksLikeTripLine(s: String): Boolean {
        val t = s.lowercase()
        return "min" in t || "hr" in t ||
            "eta" in t || "·" in s || "km left" in t
    }

    private fun extractDistance(s: String): Pair<String, Int?>? {
        val m = distanceFirst.find(s) ?: distanceAny.find(s) ?: return null
        val raw = m.groupValues[1].replace(',', '.')
        val unit = m.groupValues[2].lowercase()
        val value = raw.toDoubleOrNull() ?: return m.value to null
        val meters = when (unit) {
            "km" -> (value * 1000).toInt()
            "mi" -> (value * 1609).toInt()
            "ft" -> (value * 0.3048).toInt()
            "yd" -> (value * 0.9144).toInt()
            else -> value.toInt()
        }
        return "$raw $unit" to meters
    }

    /** Trip remaining beyond this makes a DESTINATION icon verdict implausible. */
    internal const val MAX_DEST_TRIP_METERS = 500

    /**
     * Remaining-trip distance in meters from a trip line such as
     * "13 min · 3.5 km · 19:22 ETA". Null when the line carries no distance.
     */
    internal fun tripRemainingMeters(tripLine: String): Int? {
        // Trip lines use non-breaking spaces ("3.5 km"), which `\s` does
        // not match — normalize first (also narrow NBSP, seen in Maps).
        val norm = tripLine.replace('\u00A0', ' ').replace('\u202F', ' ')
        val m = distanceAny.find(norm) ?: return null
        val raw = m.groupValues[1].replace(',', '.')
        val value = raw.toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "km" -> (value * 1000).toInt()
            "mi" -> (value * 1609).toInt()
            "ft" -> (value * 0.3048).toInt()
            "yd" -> (value * 0.9144).toInt()
            else -> value.toInt()
        }
    }

    /**
     * Whether a DESTINATION icon verdict is plausible given the remaining
     * trip distance. Far from arrival a DEST lookalike (field: roundabout
     * loop at 3.5 km remaining) must degrade to UNKNOWN (`?`), never DEST.
     * No distance info means no suppression.
     */
    internal fun isPlausibleDestination(tripLine: String): Boolean {
        val remaining = tripRemainingMeters(tripLine) ?: return true
        return remaining <= MAX_DEST_TRIP_METERS
    }

    /** First non-UNKNOWN maneuver across lines in priority order. */
    internal fun detectManeuver(lines: List<String>): NavManeuver {
        for (line in lines) {
            val m = detectManeuver(line)
            if (m != NavManeuver.UNKNOWN) return m
        }
        return NavManeuver.UNKNOWN
    }

    internal fun detectManeuver(s: String): NavManeuver {
        val t = s.lowercase()
        // Word-boundary matching throughout: bare substring checks caused
        // false STRAIGHT via "head" in "ahead". Order: sharp/slight/bear
        // before plain turns, EXIT/ramp/merge before turns, STRAIGHT last.
        fun has(vararg words: String): Boolean = words.any { it in t }
        fun regex(p: String): Boolean = Regex(p).containsMatchIn(t)
        return when {
            regex("""\bu[\s-]?turn\b""") -> NavManeuver.UTURN
            has("sharp left") -> NavManeuver.SHARP_LEFT
            has("sharp right") -> NavManeuver.SHARP_RIGHT
            has("slight left", "bear left") -> NavManeuver.SLIGHT_LEFT
            has("slight right", "bear right") -> NavManeuver.SLIGHT_RIGHT
            // Roundabouts and exits carry no side for the watch display — keep
            // these guards ahead of the exit/turn/straight branches so e.g.
            // "At the roundabout, take the 2nd exit…" or "Take exit 5 toward…"
            // stay UNKNOWN instead of falling into EXIT/STRAIGHT.
            has("roundabout", "rotary", "traffic circle") ->
                NavManeuver.UNKNOWN
            has("keep left", "stay left") -> NavManeuver.KEEP_LEFT
            has("keep right", "stay right") -> NavManeuver.KEEP_RIGHT
            regex("""\bexit\b""") || has("take the exit", "take exit", "off ramp")
                || has("take the ramp", "take ramp", "merge onto", "merge on") ->
                NavManeuver.UNKNOWN
            has("turn left") -> NavManeuver.TURN_LEFT
            has("turn right") -> NavManeuver.TURN_RIGHT
            has("arriv", "destination", "you have arrived") ->
                NavManeuver.DESTINATION
            has("go straight", "straight ahead", "continue straight", "continue on", "stay on")
                // "Head north/south/…" is a compass orientation, not a maneuver:
                // field case maps_starpath_head_south. Only explicit
                // "Head straight/up" counts; the icon decides the rest.
                || regex("""\bhead\s+(straight|up)\b""") ->
                NavManeuver.STRAIGHT
            // Bare "toward X" with no turn verb carries no direction: field
            // case turn_right_with_toward_text showed a right-turn arrow with
            // "toward P. Nguyen Co Thach" text. Leave UNKNOWN so the icon
            // classifier decides; never fake-straight.
            // Bare "continue" (e.g. "Continue on Nguyen Hue") implies straight.
            regex("""\bcontinue\b""") -> NavManeuver.STRAIGHT
            else -> NavManeuver.UNKNOWN
        }
    }

    private fun extractStreet(head: String, body: String): String {
        // "Turn left onto Nguyen Hue" -> "Nguyen Hue".
        val onto = Regex("""(?i)\b(?:onto|on|toward(?:s)?)\b\s+(.+)""").find(head)
        if (onto != null) return onto.groupValues[1].trim().trimEnd('.')
        return body.ifBlank { head }.trim()
    }
}
