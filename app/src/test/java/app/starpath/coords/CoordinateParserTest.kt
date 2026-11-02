package app.starpath.coords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun `auto detects decimal and accepts the geo prefix`() {
        val decimal = CoordinateParser.parse("41.40363, 2.17436") as CoordinateResult.Found
        assertEquals(InputKind.DECIMAL, decimal.kind)
        assertEquals(41.40363, decimal.point.latitude, 1e-9)

        val geo = CoordinateParser.parse("geo:41.40363,2.17436") as CoordinateResult.Found
        assertEquals(41.40363, geo.point.latitude, 1e-9)
        assertEquals(2.17436, geo.point.longitude, 1e-9)

        val withQuery = CoordinateParser.parse("geo:41.40363,2.17436?z=17") as CoordinateResult.Found
        assertEquals(41.40363, withQuery.point.latitude, 1e-9)
        assertEquals(2.17436, withQuery.point.longitude, 1e-9)
    }

    @Test
    fun `auto detects DMS and DDM`() {
        val dms = CoordinateParser.parse("41°24'13.1\" N, 2°10'27.7\" E") as CoordinateResult.Found
        assertEquals(InputKind.DMS, dms.kind)

        val ddm = CoordinateParser.parse("N 41° 24.218 E 002° 10.462") as CoordinateResult.Found
        assertEquals(InputKind.DDM, ddm.kind)
    }

    @Test
    fun `auto detects a full plus code`() {
        val result = CoordinateParser.parse("8FVC9G8F+6X") as CoordinateResult.Found
        assertEquals(InputKind.PLUS_CODE, result.kind)
        assertEquals(47.365590, result.point.latitude, 0.0001)
        assertEquals(8.524997, result.point.longitude, 0.0001)
    }

    @Test
    fun `auto detects full plus code with trailing locality address text`() {
        val result = CoordinateParser.parse("8FVC9G8F+6X Zurich, Switzerland") as CoordinateResult.Found
        assertEquals(InputKind.PLUS_CODE, result.kind)
        assertEquals(47.365590, result.point.latitude, 0.0001)
        assertEquals(8.524997, result.point.longitude, 0.0001)
        assertTrue(result.note?.contains("Zurich") == true)
    }

    @Test
    fun `resolves local plus code with address using offline city anchor`() {
        val result = CoordinateParser.parse("8QHG+VV Kim Anh, Ha Noi, Vietnam") as CoordinateResult.Found
        assertEquals(InputKind.PLUS_CODE, result.kind)
        // Kim Anh, Soc Son, Hanoi is at approx 21.3297, 105.7772
        assertEquals(21.3297, result.point.latitude, 0.001)
        assertEquals(105.7772, result.point.longitude, 0.001)
        assertTrue(result.note?.contains("Kim Anh, Ha Noi, Vietnam") == true)

        // Also test with Vietnamese diacritics
        val withAccents = CoordinateParser.parse("8QHG+VV Kim Anh, Hà Nội, Việt Nam") as CoordinateResult.Found
        assertEquals(InputKind.PLUS_CODE, withAccents.kind)
        assertEquals(result.point.latitude, withAccents.point.latitude, 1e-9)
        assertEquals(result.point.longitude, withAccents.point.longitude, 1e-9)
    }

    @Test
    fun `short plus code with unknown locality fails strictly even with reference`() {
        // Unknown locality without reference
        val rejected = CoordinateParser.parse("8QJW+GXV NOT EXIST") as CoordinateResult.Failed
        assertEquals(CoordinateResult.Reason.UNKNOWN_LOCALITY, rejected.reason)
        assertEquals("NOT EXIST", rejected.detail)

        // Unknown locality WITH reference point must still fail instead of resolving near reference
        val rejectedWithRef = CoordinateParser.parse(
            "8QJW+GXV NOT EXIST",
            reference = GeoPoint(21.0285, 105.8542),
        ) as CoordinateResult.Failed
        assertEquals(CoordinateResult.Reason.UNKNOWN_LOCALITY, rejectedWithRef.reason)
        assertEquals("NOT EXIST", rejectedWithRef.detail)
    }

    @Test
    fun `bare short plus code requires a reference`() {
        val rejected = CoordinateParser.parse("9G8F+6X") as CoordinateResult.Failed
        assertEquals(CoordinateResult.Reason.SHORT_PLUS_CODE_NEEDS_REFERENCE, rejected.reason)

        val found = CoordinateParser.parse(
            "9G8F+6X",
            reference = GeoPoint(47.365590, 8.524997),
        ) as CoordinateResult.Found
        assertEquals(InputKind.PLUS_CODE, found.kind)
        assertEquals(47.365590, found.point.latitude, 0.0001)
    }

    @Test
    fun `auto detects geohash`() {
        val result = CoordinateParser.parse("sp3e93r3d") as CoordinateResult.Found
        assertEquals(InputKind.GEOHASH, result.kind)
        assertTrue(result.point.latitude in 41.0..42.0)
        assertTrue(result.point.longitude in 2.0..3.0)
    }

    @Test
    fun `explicit format wins`() {
        val result = CoordinateParser.parse(
            "41°24'13.1\" N, 2°10'27.7\" E",
            InputKind.DECIMAL,
        )
        assertTrue(result is CoordinateResult.Failed)
    }

    @Test
    fun `formats all representations without a device`() {
        val point = GeoPoint(41.40363, 2.17436)
        assertEquals("41.40363, 2.17436", CoordinateParser.formatDecimal(point))
        assertEquals(12, CoordinateParser.formatPlusCode(point).replace("+", "").length)
        assertEquals(9, CoordinateParser.formatGeohash(point).length)
        assertEquals("41°24′13.1″ N 2°10′27.7″ E", DegreeFormats.dms(point))
        assertEquals("N 41° 24.218 E 002° 10.462", DegreeFormats.ddm(point))
    }

    @Test
    fun `rejects empty and malformed input`() {
        assertEquals(
            CoordinateResult.Failed(CoordinateResult.Reason.INVALID),
            CoordinateParser.parse(""),
        )
        assertEquals(
            CoordinateResult.Failed(CoordinateResult.Reason.INVALID),
            CoordinateParser.parse("some place name"),
        )
    }
}
