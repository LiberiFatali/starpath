package app.starpath.coords

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class DegreeKind { DECIMAL, DMS, DDM }

data class ParsedDegrees(val point: GeoPoint, val kind: DegreeKind)

/**
 * Degrees written as decimal degrees, degrees and decimal minutes (DDM), or
 * degrees, minutes and seconds (DMS).
 *
 * Formatting and parsing follow osm2gmaps (GPL-3.0-or-later). Parsing is
 * deliberately tolerant: hemispheres may come before or after each number,
 * and the usual Unicode primes and quotes are normalised first.
 */
object DegreeFormats {

    /** 41°24′13.1″ N 2°10′27.7″ E */
    fun dms(point: GeoPoint): String =
        "${dmsPart(point.latitude, 'N', 'S')} ${dmsPart(point.longitude, 'E', 'W')}"

    /** N 41° 24.218 E 002° 10.462 */
    fun ddm(point: GeoPoint): String =
        "${ddmPart(point.latitude, 'N', 'S', 2)} ${ddmPart(point.longitude, 'E', 'W', 3)}"

    /** Decimal degrees, DMS or DDM in [text]; null when none is found. */
    fun parse(text: String): ParsedDegrees? {
        val normalized = normalize(text).trim()
        if (normalized.isEmpty() || normalized.length > 200) return null
        for (pair in wholePairs) {
            val match = pair.matchEntire(normalized) ?: continue
            return fromComponents(match.groupValues)
        }
        return null
    }

    private fun dmsPart(value: Double, positive: Char, negative: Char): String {
        // Round once, on tenths of seconds, so that no part reaches 60.
        val tenths = (abs(value) * 36000).roundToLong()
        val degrees = tenths / 36000
        val minutes = tenths % 36000 / 600
        val seconds = tenths % 600
        return String.format(
            Locale.ROOT, "%d°%02d′%02d.%d″ %c",
            degrees, minutes, seconds / 10, seconds % 10,
            if (value >= 0 || tenths == 0L) positive else negative,
        )
    }

    private fun ddmPart(value: Double, positive: Char, negative: Char, digits: Int): String {
        // Round once, on thousandths of minutes, so that minutes never reach 60.
        val thousandths = (abs(value) * 60_000).roundToLong()
        return String.format(
            Locale.ROOT, "%c %0${digits}d° %02d.%03d",
            if (value >= 0 || thousandths == 0L) positive else negative,
            thousandths / 60_000, thousandths % 60_000 / 1000, thousandths % 1000,
        )
    }

    private data class Component(
        val value: Double,
        val hemisphere: Char?,
        val hasMinutes: Boolean,
        val hasSeconds: Boolean,
    )

    private fun fromComponents(groups: List<String>): ParsedDegrees? {
        // Groups per component: hemisphere before, decimal degrees, degrees,
        // minutes, seconds, hemisphere after.
        fun component(offset: Int): Component? {
            val hemisphere = (groups[offset].ifEmpty { groups[offset + 5] }).firstOrNull()?.uppercaseChar()
            val decimal = groups[offset + 1]
            val hasMinutes = groups[offset + 3].isNotEmpty()
            val hasSeconds = groups[offset + 4].isNotEmpty()
            val value = if (decimal.isNotEmpty()) {
                decimal.toDoubleOrNull() ?: return null
            } else {
                val degrees = groups[offset + 2].toDoubleOrNull() ?: return null
                val minutes = groups[offset + 3].toDoubleOrNull() ?: 0.0
                val seconds = groups[offset + 4].toDoubleOrNull() ?: 0.0
                if (minutes >= 60 || seconds >= 60) return null
                val magnitude = abs(degrees) + minutes / 60 + seconds / 3600
                if (groups[offset + 2].startsWith("-")) -magnitude else magnitude
            }
            val signed = if (hemisphere == 'S' || hemisphere == 'W') -abs(value) else value
            return Component(signed, hemisphere, hasMinutes, hasSeconds)
        }

        var first = component(1) ?: return null
        var second = component(7) ?: return null
        // "E 2.17, N 41.40": longitude first
        if (first.hemisphere in setOf('E', 'W') && second.hemisphere in setOf('N', 'S', null)) {
            val oldFirst = first
            first = second
            second = oldFirst
        }
        if (first.hemisphere in setOf('E', 'W') || second.hemisphere in setOf('N', 'S')) return null
        val point = GeoPoint.of(first.value, second.value) ?: return null
        val kind = when {
            first.hasSeconds || second.hasSeconds -> DegreeKind.DMS
            first.hasMinutes || second.hasMinutes -> DegreeKind.DDM
            else -> DegreeKind.DECIMAL
        }
        return ParsedDegrees(point, kind)
    }

    private fun normalize(text: String) = text
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace('′', '\'')
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('″', '"')
        .replace('”', '"')
        .replace('“', '"')
        .replace("''", "\"")
        .replace('º', '°')
        .replace('˚', '°')

    private const val HEMISPHERE = "[NSEWnsew]"
    private const val DECIMALS = """\.\d{1,15}"""
    private const val DEGREES_DECIMAL = """[-+]?\d{1,3}${DECIMALS}"""
    private const val DEGREES = """[-+]?\d{1,3}"""
    private const val MINUTES_OR_SECONDS = """\d{1,2}(?:${DECIMALS})?"""

    // One coordinate: decimal degrees, or degrees with optional minutes and
    // seconds. Groups: decimal degrees, degrees, minutes, seconds.
    private const val NUMBER =
        """(?:(${DEGREES_DECIMAL})\s*°?|(${DEGREES})\s*°?(?:\s*(${MINUTES_OR_SECONDS})\s*'?(?:\s*(${MINUTES_OR_SECONDS})\s*"?)?)?)"""
    private const val LETTER_BEFORE = """(${HEMISPHERE})\s*${NUMBER}()"""
    private const val LETTER_AFTER = """()${NUMBER}\s*(${HEMISPHERE})?"""

    // Letters before or after both numbers, not mixed.
    private val wholePairs = listOf(
        Regex("""${LETTER_BEFORE}\s*[,;]?\s*${LETTER_BEFORE}"""),
        Regex("""${LETTER_AFTER}\s*[,;]?\s*${LETTER_AFTER}"""),
    )
}
