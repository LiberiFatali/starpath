package app.starpath.coords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DegreeFormatsTest {

    private val point = GeoPoint(41.40363, 2.17436)

    @Test
    fun `formats DMS and DDM at fixed precision`() {
        assertEquals("41°24′13.1″ N 2°10′27.7″ E", DegreeFormats.dms(point))
        assertEquals("N 41° 24.218 E 002° 10.462", DegreeFormats.ddm(point))
    }

    @Test
    fun `formats southern and western hemispheres`() {
        val southWest = GeoPoint(-33.86882, -70.66927)
        assertEquals("33°52′07.8″ S 70°40′09.4″ W", DegreeFormats.dms(southWest))
        assertEquals("S 33° 52.129 W 070° 40.156", DegreeFormats.ddm(southWest))
    }

    @Test
    fun `parses decimal degrees with symbols and labels`() {
        val parsed = DegreeFormats.parse("41.40363, 2.17436")!!
        assertEquals(DegreeKind.DECIMAL, parsed.kind)
        assertEquals(41.40363, parsed.point.latitude, 1e-9)
        assertEquals(2.17436, parsed.point.longitude, 1e-9)

        val labelled = DegreeFormats.parse("41.40363 N, 2.17436 E")!!
        assertEquals(41.40363, labelled.point.latitude, 1e-9)
        assertEquals(2.17436, labelled.point.longitude, 1e-9)
    }

    @Test
    fun `parses DMS with hemispheres after or before`() {
        val after = DegreeFormats.parse(
            "41°24'13.1\" N, 2°10'27.7\" E",
        )!!
        assertEquals(DegreeKind.DMS, after.kind)
        assertEquals(41.40363, after.point.latitude, 1e-5)
        assertEquals(2.17436, after.point.longitude, 1e-5)

        val before = DegreeFormats.parse("N 41° 24' 13.1\" E 2° 10' 27.7\"")!!
        assertEquals(41.40363, before.point.latitude, 1e-5)
        assertEquals(2.17436, before.point.longitude, 1e-5)
    }

    @Test
    fun `parses DDM with hemisphere before`() {
        val parsed = DegreeFormats.parse("N 41° 24.218 E 002° 10.462")!!
        assertEquals(DegreeKind.DDM, parsed.kind)
        assertEquals(41.40363, parsed.point.latitude, 1e-5)
        assertEquals(2.17436, parsed.point.longitude, 1e-5)
    }

    @Test
    fun `assumes latitude first when unlabelled`() {
        val parsed = DegreeFormats.parse("2.17436, 41.40363")!!
        assertEquals(2.17436, parsed.point.latitude, 1e-9)
        assertEquals(41.40363, parsed.point.longitude, 1e-9)
    }

    @Test
    fun `swaps longitude first when hemispheres label it`() {
        val parsed = DegreeFormats.parse("E 2.17436, N 41.40363")!!
        assertEquals(41.40363, parsed.point.latitude, 1e-9)
        assertEquals(2.17436, parsed.point.longitude, 1e-9)
    }

    @Test
    fun `rejects out of range values and invalid minutes`() {
        assertNull(DegreeFormats.parse("91, 0"))
        assertNull(DegreeFormats.parse("0, 181"))
        assertNull(DegreeFormats.parse("41°75'00\" N, 2°10'27.7\" E"))
        assertNull(DegreeFormats.parse("hello"))
        assertNull(DegreeFormats.parse(""))
    }

    @Test
    fun `does not overrun sixty after rounding`() {
        val nearBoundary = GeoPoint(41.0 + 59.99999 / 60, 2.0)
        assertEquals("42°00′00.0″ N 2°00′00.0″ E", DegreeFormats.dms(nearBoundary))
        assertEquals("N 42° 00.000 E 002° 00.000", DegreeFormats.ddm(nearBoundary))
        assertNotNull(DegreeFormats.parse(DegreeFormats.dms(nearBoundary)))
        assertNotNull(DegreeFormats.parse(DegreeFormats.ddm(nearBoundary)))
    }
}
