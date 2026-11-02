package app.starpath.coords

/**
 * Geohash: a cell of the world as a string, each character halving it five
 * more times (https://en.wikipedia.org/wiki/Geohash). Public domain.
 *
 * Ported from osm2gmaps (GPL-3.0-or-later).
 */
object Geohash {

    const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** 9 characters: a cell of about 5 m. */
    fun encode(latitude: Double, longitude: Double, length: Int = 9): String {
        val lat = doubleArrayOf(-90.0, 90.0)
        val lng = doubleArrayOf(-180.0, 180.0)
        val out = StringBuilder(length)
        var even = true
        var value = 0
        var bits = 0
        while (out.length < length) {
            val interval = if (even) lng else lat
            val coordinate = if (even) longitude else latitude
            val middle = (interval[0] + interval[1]) / 2
            value = value shl 1
            if (coordinate >= middle) {
                value = value or 1
                interval[0] = middle
            } else {
                interval[1] = middle
            }
            even = !even
            if (++bits == 5) {
                out.append(ALPHABET[value])
                value = 0
                bits = 0
            }
        }
        return out.toString()
    }

    /** Centre of the cell; null for a character outside the alphabet. */
    fun decode(geohash: String): GeoPoint? {
        val (south, west, north, east) = bounds(geohash) ?: return null
        return GeoPoint.of((south + north) / 2, (west + east) / 2)
    }

    /** South, west, north and east edges of the cell. */
    fun bounds(geohash: String): DoubleArray? {
        if (geohash.isEmpty() || geohash.length > 12) return null
        val lat = doubleArrayOf(-90.0, 90.0)
        val lng = doubleArrayOf(-180.0, 180.0)
        var even = true
        for (char in geohash.lowercase()) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            for (bit in 4 downTo 0) {
                val interval = if (even) lng else lat
                val middle = (interval[0] + interval[1]) / 2
                if ((value shr bit) and 1 == 1) interval[0] = middle else interval[1] = middle
                even = !even
            }
        }
        return doubleArrayOf(lat[0], lng[0], lat[1], lng[1])
    }
}
