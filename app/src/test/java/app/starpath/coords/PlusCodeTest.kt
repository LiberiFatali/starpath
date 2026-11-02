package app.starpath.coords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusCodeTest {

    private val zurich = GeoPoint(47.365590, 8.524997)

    @Test
    fun `matches a known code`() {
        assertEquals("8FVC9G8F+6X", PlusCode.encode(zurich, 10))
    }

    @Test
    fun `full code has twelve digits and round trips`() {
        val code = PlusCode.encode(zurich)
        assertTrue(code.contains("+"))
        assertEquals(12, code.replace("+", "").length)
        assertTrue(PlusCode.isFull(code))

        val decoded = PlusCode.decode(code)
        assertNotNull(decoded)
        assertEquals(zurich.latitude, decoded!!.latitude, 0.0001)
        assertEquals(zurich.longitude, decoded.longitude, 0.0001)
    }

    @Test
    fun `whitespace and lower case are accepted`() {
        val code = PlusCode.encode(zurich)
        val spaced = code.lowercase().chunked(4).joinToString(" ")
        assertEquals(zurich.latitude, PlusCode.decode(spaced)!!.latitude, 0.00001)
    }

    @Test
    fun `short code is recovered near a reference`() {
        assertTrue(PlusCode.isShort("9G8F+6X"))
        assertFalse(PlusCode.isFull("9G8F+6X"))

        val recovered = PlusCode.recover("9G8F+6X", zurich)
        assertNotNull(recovered)
        assertEquals(PlusCode.decode("8FVC9G8F+6X")!!.latitude, recovered!!.latitude, 1e-9)
        assertEquals(PlusCode.decode("8FVC9G8F+6X")!!.longitude, recovered.longitude, 1e-9)
    }

    @Test
    fun `extracts plus code and locality text`() {
        val extracted1 = PlusCode.extract("8QHG+VV Kim Anh, Ha Noi, Vietnam")
        assertNotNull(extracted1)
        assertEquals("8QHG+VV", extracted1!!.code)
        assertEquals("Kim Anh, Ha Noi, Vietnam", extracted1.locality)

        val extracted2 = PlusCode.extract("Kim Anh, Hà Nội 8QHG+VV")
        assertNotNull(extracted2)
        assertEquals("8QHG+VV", extracted2!!.code)
        assertEquals("Kim Anh, Hà Nội", extracted2.locality)

        val extracted3 = PlusCode.extract("7P288QHG+VV Kim Anh, Ha Noi, Vietnam")
        assertNotNull(extracted3)
        assertEquals("7P288QHG+VV", extracted3!!.code)
        assertEquals("Kim Anh, Ha Noi, Vietnam", extracted3.locality)

        val bare = PlusCode.extract("8FVC9G8F+6X")
        assertNotNull(bare)
        assertEquals("8FVC9G8F+6X", bare!!.code)
        assertNull(bare.locality)
    }

    @Test
    fun `city anchors matches city names with or without accents`() {
        val hanoiPoint = CityAnchors.find("Kim Anh, Ha Noi, Vietnam")
        assertNotNull(hanoiPoint)
        assertEquals(21.0285, hanoiPoint!!.latitude, 0.001)

        val hanoiDiacritics = CityAnchors.find("Kim Anh, Hà Nội, Việt Nam")
        assertNotNull(hanoiDiacritics)
        assertEquals(21.0285, hanoiDiacritics!!.latitude, 0.001)

        val saigon = CityAnchors.find("Quận 1, Thành phố Hồ Chí Minh")
        assertNotNull(saigon)
        assertEquals(10.8231, saigon!!.latitude, 0.001)

        val zurichPoint = CityAnchors.find("Zurich, Switzerland")
        assertNotNull(zurichPoint)
        assertEquals(47.3769, zurichPoint!!.latitude, 0.001)

        assertNull(CityAnchors.find("Unknown Place 12345"))
    }

    @Test
    fun `invalid text is rejected`() {
        assertFalse(PlusCode.isValid("not a plus code"))
        assertNull(PlusCode.decode("not a plus code"))
        assertNull(PlusCode.extract("not a plus code"))
    }
}
