package app.starpath.nav

/**
 * Parses the Google Maps navigation notification into a [NavUpdate].
 *
 * Pure Kotlin (no Android dependency on the hot path inputs) so it stays
 * unit-testable. Google changes this layout roughly once a year — when
 * parsing breaks, fix HERE and add the captured dump to [GMapsParserTest].
 *
 * Inputs mirror [android.app.Notification.extras]:
 * @param title android.title — usually the maneuver instruction, e.g.
 *   "Turn left in 200 m onto Nguyen Hue" / "Rẽ trái sau 200 m".
 * @param text android.text — usually the street / next-step detail.
 * @param bigText android.bigText — expanded style text, preferred when present.
 * @param textLines android.textLines — inbox-style lines, last resort.
 */
object GMapsParser {

    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private val distanceFirst = Regex(
        """(?i)(?:in|after|sau)\s+([\d.,]+)\s*(km|m|mi|ft)\b"""
    )
    private val distanceAny = Regex("""([\d.,]+)\s*(km|m|mi|ft)\b""")

    fun parse(
        title: String?,
        text: String?,
        bigText: String?,
        textLines: List<String>,
    ): NavUpdate? {
        val lines = buildList {
            bigText?.takeIf { it.isNotBlank() }?.let { add(it) }
            title?.takeIf { it.isNotBlank() }?.let { add(it) }
            text?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(textLines.filter { it.isNotBlank() })
        }
        if (lines.isEmpty()) return null
        val head = lines.first()

        if (isRerouting(head)) {
            return NavUpdate(
                maneuver = NavManeuver.UNKNOWN,
                distanceText = "",
                distanceMeters = null,
                street = head,
                tripLine = "",
                state = NavState.REROUTING,
            )
        }

        val body = lines.getOrNull(1).orEmpty()
        val trip = lines.drop(2).firstOrNull { looksLikeTripLine(it) }.orEmpty()
        val (distanceText, distanceMeters) = extractDistance(head)
            ?: extractDistance(body)
            ?: ("" to null)

        return NavUpdate(
            maneuver = detectManeuver(head),
            distanceText = distanceText,
            distanceMeters = distanceMeters,
            street = extractStreet(head, body),
            tripLine = trip.ifBlank { body.takeIf { it != head }.orEmpty() },
            state = NavState.ENROUTE,
        )
    }

    private fun isRerouting(s: String): Boolean {
        val t = s.lowercase()
        return "rerouting" in t || "finding" in t && "route" in t ||
            "đang tìm" in t || "tìm lại" in t || "tuyến đường" in t && "mới" in t
    }

    private fun looksLikeTripLine(s: String): Boolean {
        val t = s.lowercase()
        return "min" in t || "phút" in t || "giờ" in t || "hr" in t ||
            "eta" in t || "·" in s || "km left" in t || "còn lại" in t
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
            else -> value.toInt()
        }
        return "$raw $unit" to meters
    }

    internal fun detectManeuver(s: String): NavManeuver {
        val t = s.lowercase()
        // English + Vietnamese, checked sharp/slight before plain turns.
        return when {
            "u-turn" in t || "quay đầu" in t -> NavManeuver.UTURN
            "sharp left" in t || "rẽ gấp trái" in t -> NavManeuver.SHARP_LEFT
            "sharp right" in t || "rẽ gấp phải" in t -> NavManeuver.SHARP_RIGHT
            "slight left" in t || "hơi" in t && "trái" in t -> NavManeuver.SLIGHT_LEFT
            "slight right" in t || "hơi" in t && "phải" in t -> NavManeuver.SLIGHT_RIGHT
            "roundabout" in t || "vòng xuyến" in t || "bùng binh" in t -> NavManeuver.ROUNDABOUT
            "keep left" in t || "giữ bên trái" in t -> NavManeuver.KEEP_LEFT
            "keep right" in t || "giữ bên phải" in t -> NavManeuver.KEEP_RIGHT
            "exit" in t || "lối ra" in t -> NavManeuver.EXIT
            "turn left" in t || "rẽ trái" in t -> NavManeuver.TURN_LEFT
            "turn right" in t || "rẽ phải" in t -> NavManeuver.TURN_RIGHT
            "arriv" in t || "destination" in t || "đến nơi" in t || "đã đến" in t ->
                NavManeuver.DESTINATION
            "straight" in t || "continue" in t || "head" in t ||
                "đi thẳng" in t || "tiếp tục" in t -> NavManeuver.STRAIGHT
            else -> NavManeuver.UNKNOWN
        }
    }

    private fun extractStreet(head: String, body: String): String {
        // "Turn left onto Nguyen Hue" -> "Nguyen Hue"; Vietnamese "rẽ trái vào X" -> "X".
        val onto = Regex("""(?i)\b(?:onto|on|toward(?:s)?)\b\s+(.+)""").find(head)
        if (onto != null) return onto.groupValues[1].trim().trimEnd('.')
        val vao = Regex("""\b(?:vào|ra|qua)\b\s+(.+)""").find(head)
        if (vao != null) {
            val rest = vao.groupValues[1].trim()
            if (' ' in rest) return rest.trimEnd('.')
        }
        return body.ifBlank { head }.trim()
    }
}
