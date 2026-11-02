package app.starpath.coords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeohashTest {

    @Test
    fun `matches the canonical example`() {
        assertEquals("u4pruydqqvj", Geohash.encode(57.64911, 10.40744, 11))
    }

    @Test
    fun `encode and decode round trip`() {
        val point = GeoPoint(41.40363, 2.17436)
        val hash = Geohash.encode(point.latitude, point.longitude)
        assertEquals(9, hash.length)

        val decoded = Geohash.decode(hash)
        assertNotNull(decoded)
        assertEquals(point.latitude, decoded!!.latitude, 0.00005)
        assertEquals(point.longitude, decoded.longitude, 0.00005)
    }

    @Test
    fun `bounds contain the original point`() {
        val point = GeoPoint(-33.86882, 151.20929)
        val bounds = Geohash.bounds(Geohash.encode(point.latitude, point.longitude, 11))!!
        assertTrue(point.latitude in bounds[0]..bounds[2])
        assertTrue(point.longitude in bounds[1]..bounds[3])
    }

    @Test
    fun `invalid characters are rejected`() {
        assertNull(Geohash.decode("abc"))
        assertNull(Geohash.decode("sp3e93r3a"))
        assertNull(Geohash.decode(""))
    }
}
