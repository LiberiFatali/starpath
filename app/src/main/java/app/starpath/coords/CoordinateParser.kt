package app.starpath.coords

import java.util.Locale

enum class InputKind { DECIMAL, DMS, DDM, PLUS_CODE, GEOHASH }

sealed interface CoordinateResult {
    data class Found(
        val point: GeoPoint,
        val kind: InputKind,
        val note: String? = null,
    ) : CoordinateResult

    data class Failed(
        val reason: Reason,
        val detail: String? = null,
    ) : CoordinateResult

    enum class Reason { INVALID, SHORT_PLUS_CODE_NEEDS_REFERENCE, UNKNOWN_LOCALITY }
}

/**
 * Reads a coordinate from any supported text format and renders it in all of
 * them. Everything is computed on-device; nothing is cached or sent.
 */
object CoordinateParser {

    private val geohashRegex = Regex("""[0-9bcdefghjkmnpqrstuvwxyz]{1,12}""", RegexOption.IGNORE_CASE)
    private val bareGeohashRegex =
        Regex("""(?=.*[0-9])(?=.*[a-z])[0-9bcdefghjkmnpqrstuvwxyz]{4,12}""", RegexOption.IGNORE_CASE)

    fun parse(
        text: String,
        preferred: InputKind? = null,
        reference: GeoPoint? = null,
    ): CoordinateResult {
        val raw = text.trim()
        if (raw.isEmpty() || raw.length > 200) {
            return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        }
        val cleaned = if (raw.startsWith("geo:", ignoreCase = true)) {
            raw.substring(4).substringBefore('?').substringBefore(';').trim()
        } else {
            raw
        }

        if (preferred != null) {
            return when (preferred) {
                InputKind.DECIMAL, InputKind.DMS, InputKind.DDM -> parseDegrees(cleaned, preferred)
                InputKind.PLUS_CODE -> parsePlusCode(cleaned, reference)
                InputKind.GEOHASH -> parseGeohash(cleaned, explicit = true)
            }
        }

        val extractedPlusCode = PlusCode.extract(cleaned)
        if (extractedPlusCode != null) {
            val result = parsePlusCode(cleaned, reference)
            if (result is CoordinateResult.Found) return result
            if (result is CoordinateResult.Failed &&
                (result.reason == CoordinateResult.Reason.SHORT_PLUS_CODE_NEEDS_REFERENCE ||
                 result.reason == CoordinateResult.Reason.UNKNOWN_LOCALITY)
            ) {
                return result
            }
        }

        if (bareGeohashRegex.matches(cleaned)) {
            val result = parseGeohash(cleaned, explicit = false)
            if (result is CoordinateResult.Found) return result
        }

        val degrees = DegreeFormats.parse(cleaned)
            ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        return CoordinateResult.Found(degrees.point, degreeKind(degrees.kind))
    }

    fun formatDecimal(point: GeoPoint): String {
        fun part(value: Double): String {
            val text = String.format(Locale.ROOT, "%.5f", value)
            return if (text == "-0.00000") "0.00000" else text
        }
        return "${part(point.latitude)}, ${part(point.longitude)}"
    }

    fun formatPlusCode(point: GeoPoint): String = PlusCode.encode(point)

    fun formatGeohash(point: GeoPoint): String = Geohash.encode(point.latitude, point.longitude)

    private fun parseDegrees(text: String, preferred: InputKind): CoordinateResult {
        val parsed = DegreeFormats.parse(text)
            ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        val kind = degreeKind(parsed.kind)
        if (kind != preferred) return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        return CoordinateResult.Found(parsed.point, kind)
    }

    private fun parsePlusCode(text: String, reference: GeoPoint?): CoordinateResult {
        val extracted = PlusCode.extract(text)
            ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        val code = extracted.code
        val locality = extracted.locality

        if (PlusCode.isFull(code)) {
            val point = PlusCode.decode(code)
                ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
            val note = if (locality != null) "Full Plus Code ($locality)" else null
            return CoordinateResult.Found(point, InputKind.PLUS_CODE, note)
        }

        // Short Plus Code with an explicit locality text: resolve strictly via CityAnchors
        if (locality != null) {
            val anchor = CityAnchors.find(locality)
                ?: return CoordinateResult.Failed(CoordinateResult.Reason.UNKNOWN_LOCALITY, locality)
            val point = PlusCode.recover(code, anchor)
                ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
            return CoordinateResult.Found(point, InputKind.PLUS_CODE, "Short Plus Code resolved for $locality")
        }

        // Bare short code: requires a reference location (e.g. live GPS fix)
        if (reference == null) {
            return CoordinateResult.Failed(CoordinateResult.Reason.SHORT_PLUS_CODE_NEEDS_REFERENCE)
        }

        val point = PlusCode.recover(code, reference)
            ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        return CoordinateResult.Found(point, InputKind.PLUS_CODE, "Short Plus Code completed near reference point")
    }

    private fun parseGeohash(text: String, explicit: Boolean): CoordinateResult {
        val regex = if (explicit) geohashRegex else bareGeohashRegex
        if (!regex.matches(text)) return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        val point = Geohash.decode(text)
            ?: return CoordinateResult.Failed(CoordinateResult.Reason.INVALID)
        return CoordinateResult.Found(point, InputKind.GEOHASH)
    }

    private fun degreeKind(kind: DegreeKind): InputKind = when (kind) {
        DegreeKind.DECIMAL -> InputKind.DECIMAL
        DegreeKind.DMS -> InputKind.DMS
        DegreeKind.DDM -> InputKind.DDM
    }
}
