package app.starpath.coords

/** A WGS 84 position, valid only between the poles and the antimeridian. */
data class GeoPoint(val latitude: Double, val longitude: Double) {

    companion object {
        fun of(latitude: Double, longitude: Double): GeoPoint? {
            if (latitude.isNaN() || longitude.isNaN()) return null
            if (latitude.isInfinite() || longitude.isInfinite()) return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return GeoPoint(latitude, longitude)
        }
    }
}
