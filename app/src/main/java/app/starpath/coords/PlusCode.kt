package app.starpath.coords

import com.google.openlocationcode.OpenLocationCode
import java.text.Normalizer

data class ExtractedPlusCode(
    val code: String,
    val locality: String?,
)

/**
 * Open Location Code ("Plus Code") helpers. Encoding and decoding happen
 * entirely on-device through Google's Apache-2.0 OpenLocationCode library.
 */
object PlusCode {

    /** 12 digits: a cell of about half a metre. */
    const val DIGITS = 12

    private val PLUS_CODE_REGEX =
        Regex("""\b([23456789CFGHJMPQRVWX]{4,8}\+[23456789CFGHJMPQRVWX]{2,7})\b""", RegexOption.IGNORE_CASE)

    fun encode(point: GeoPoint, digits: Int = DIGITS): String =
        OpenLocationCode(point.latitude, point.longitude, digits).code

    /** Centre of a full code; null for short or invalid codes. */
    fun decode(text: String): GeoPoint? {
        val code = normalize(text)
        if (code.isEmpty()) return null
        val area = runCatching {
            OpenLocationCode(code).takeIf { it.isFull }?.decode()
        }.getOrNull() ?: return null
        return GeoPoint.of(area.centerLatitude, area.centerLongitude)
    }

    /** A short code completed near [reference]. */
    fun recover(text: String, reference: GeoPoint): GeoPoint? {
        val code = normalize(text)
        if (code.isEmpty()) return null
        val full = runCatching {
            OpenLocationCode(code).recover(reference.latitude, reference.longitude).code
        }.getOrNull() ?: return null
        return decode(full)
    }

    /** Extracts a Plus Code and optional trailing/surrounding locality text from an input string. */
    fun extract(text: String): ExtractedPlusCode? {
        val match = PLUS_CODE_REGEX.find(text) ?: return null
        val candidate = normalize(match.value)
        if (!isValid(candidate)) return null
        val remainder = text.removeRange(match.range)
            .trim(',', ' ', ';', '·', '\t', '\n')
            .ifEmpty { null }
        return ExtractedPlusCode(candidate, remainder)
    }

    fun isValid(text: String): Boolean =
        runCatching { OpenLocationCode.isValidCode(normalize(text)) }.getOrDefault(false)

    fun isFull(text: String): Boolean =
        runCatching { OpenLocationCode(normalize(text)).isFull }.getOrDefault(false)

    fun isShort(text: String): Boolean =
        runCatching { OpenLocationCode(normalize(text)).isShort }.getOrDefault(false)

    private fun normalize(text: String) = text.filterNot { it.isWhitespace() }.uppercase()
}

/**
 * Offline regional reference coordinates for major cities and regions.
 * Open Location Code short codes only require an anchor within ~50–80 km (half a degree)
 * to reconstruct the exact cell.
 */
object CityAnchors {

    private val ANCHORS = listOf(
        // Vietnam
        "ha noi" to GeoPoint(21.0285, 105.8542),
        "hanoi" to GeoPoint(21.0285, 105.8542),
        "ho chi minh" to GeoPoint(10.8231, 106.6297),
        "hochiminh" to GeoPoint(10.8231, 106.6297),
        "saigon" to GeoPoint(10.8231, 106.6297),
        "sai gon" to GeoPoint(10.8231, 106.6297),
        "da nang" to GeoPoint(16.0544, 108.2022),
        "danang" to GeoPoint(16.0544, 108.2022),
        "hai phong" to GeoPoint(20.8449, 106.6881),
        "haiphong" to GeoPoint(20.8449, 106.6881),
        "can tho" to GeoPoint(10.0452, 105.7469),
        "cantho" to GeoPoint(10.0452, 105.7469),
        "nha trang" to GeoPoint(12.2388, 109.1967),
        "nhatrang" to GeoPoint(12.2388, 109.1967),
        "hue" to GeoPoint(16.4637, 107.5909),
        "vung tau" to GeoPoint(10.3460, 107.0843),
        "vungtau" to GeoPoint(10.3460, 107.0843),
        "da lat" to GeoPoint(11.9404, 108.4583),
        "dalat" to GeoPoint(11.9404, 108.4583),
        "quy nhon" to GeoPoint(13.7830, 109.2197),
        "ha long" to GeoPoint(20.9505, 107.0734),
        "vinh" to GeoPoint(18.6796, 105.6813),
        "soc son" to GeoPoint(21.2571, 105.8500),
        "bac ninh" to GeoPoint(21.1861, 106.0763),
        "thai nguyen" to GeoPoint(21.5928, 105.8442),

        // Global hubs & capitals
        "zurich" to GeoPoint(47.3769, 8.5417),
        "geneva" to GeoPoint(46.2044, 6.1432),
        "paris" to GeoPoint(48.8566, 2.3522),
        "london" to GeoPoint(51.5074, -0.1278),
        "berlin" to GeoPoint(52.5200, 13.4050),
        "tokyo" to GeoPoint(35.6762, 139.6503),
        "osaka" to GeoPoint(34.6937, 135.5023),
        "seoul" to GeoPoint(37.5665, 126.9780),
        "singapore" to GeoPoint(1.3521, 103.8198),
        "bangkok" to GeoPoint(13.7563, 100.5018),
        "kuala lumpur" to GeoPoint(3.1390, 101.6869),
        "jakarta" to GeoPoint(6.2088, 106.8456),
        "manila" to GeoPoint(14.5995, 120.9842),
        "taipei" to GeoPoint(25.0330, 121.5654),
        "beijing" to GeoPoint(39.9042, 116.4074),
        "shanghai" to GeoPoint(31.2304, 121.4737),
        "hong kong" to GeoPoint(22.3193, 114.1694),
        "hongkong" to GeoPoint(22.3193, 114.1694),
        "sydney" to GeoPoint(-33.8688, 151.2093),
        "melbourne" to GeoPoint(-37.8136, 144.9631),
        "auckland" to GeoPoint(-36.8485, 174.7633),
        "new york" to GeoPoint(40.7128, -74.0060),
        "san francisco" to GeoPoint(37.7749, -122.4194),
        "los angeles" to GeoPoint(34.0522, -118.2437),
        "chicago" to GeoPoint(41.8781, -87.6298),
        "toronto" to GeoPoint(43.6532, -79.3832),
        "vancouver" to GeoPoint(49.2827, -123.1207),
        "mexico city" to GeoPoint(19.4326, -99.1332),
        "sao paulo" to GeoPoint(-23.5505, -46.6333),
        "buenos aires" to GeoPoint(-34.6037, -58.3816),
        "cairo" to GeoPoint(30.0444, 31.2357),
        "dubai" to GeoPoint(25.2048, 55.2708),
        "cape town" to GeoPoint(-33.9249, 18.4241),
        "praia" to GeoPoint(14.9330, -23.5133),
    )

    fun find(localityText: String): GeoPoint? {
        val normalized = normalize(localityText)
        for ((name, point) in ANCHORS) {
            val regex = Regex("""\b${Regex.escape(name)}\b""")
            if (regex.containsMatchIn(normalized)) {
                return point
            }
        }
        return null
    }

    private fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return nfd.replace("\\p{M}".toRegex(), "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
    }
}
