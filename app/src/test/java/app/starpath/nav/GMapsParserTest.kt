package app.starpath.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GMapsParserTest {

    @Test
    fun `english turn with onto street`() {
        val u = GMapsParser.parse(
            title = "Turn left in 200 m onto Nguyen Hue",
            text = "Nguyen Hue",
            bigText = null,
            textLines = emptyList(),
        )!!
        assertEquals(NavManeuver.TURN_LEFT, u.maneuver)
        assertEquals("200 m", u.distanceText)
        assertEquals(200, u.distanceMeters)
        assertEquals("Nguyen Hue", u.street)
        assertEquals(NavState.ENROUTE, u.state)
    }

    @Test
    fun `vietnamese turn`() {
        val u = GMapsParser.parse(
            title = "Rẽ phải sau 1,2 km vào Lê Lợi",
            text = "Lê Lợi",
            bigText = null,
            textLines = emptyList(),
        )!!
        assertEquals(NavManeuver.TURN_RIGHT, u.maneuver)
        assertEquals(1200, u.distanceMeters)
        assertEquals("Lê Lợi", u.street)
    }

    @Test
    fun `roundabout and uturn`() {
        assertEquals(
            NavManeuver.ROUNDABOUT,
            GMapsParser.parse("At the roundabout, take the 2nd exit in 500 m", null, null, emptyList())!!.maneuver,
        )
        assertEquals(
            NavManeuver.UTURN,
            GMapsParser.parse("Make a U-turn in 100 m", null, null, emptyList())!!.maneuver,
        )
        assertEquals(
            NavManeuver.UTURN,
            GMapsParser.parse("Quay đầu sau 100 m", null, null, emptyList())!!.maneuver,
        )
    }

    @Test
    fun `sharp beats plain turn, keep beats turn`() {
        assertEquals(
            NavManeuver.SHARP_LEFT,
            GMapsParser.detectManeuver("Sharp left in 50 m"),
        )
        assertEquals(
            NavManeuver.KEEP_RIGHT,
            GMapsParser.detectManeuver("Keep right to stay on Highway 1"),
        )
    }

    @Test
    fun `rerouting detected EN and VI`() {
        assertEquals(
            NavState.REROUTING,
            GMapsParser.parse("Rerouting…", null, null, emptyList())!!.state,
        )
        assertEquals(
            NavState.REROUTING,
            GMapsParser.parse("Đang tìm tuyến đường mới", null, null, emptyList())!!.state,
        )
    }

    @Test
    fun `trip line picked from third line`() {
        val u = GMapsParser.parse(
            title = "Turn right in 300 m",
            text = "Cach Mang Thang 8",
            bigText = null,
            textLines = listOf("12 min · 3.2 km left"),
        )!!
        assertEquals("12 min · 3.2 km left", u.tripLine)
    }

    @Test
    fun `blank notification returns null`() {
        assertNull(GMapsParser.parse(null, null, null, emptyList()))
        assertNull(GMapsParser.parse("  ", " ", null, emptyList()))
    }

    @Test
    fun `formatter fits round screen`() {
        val u = NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "12 min", NavState.ENROUTE)
        assertEquals("◀◀ 200 m", NavFormatter.title(u))
        val long = u.copy(street = "Ludwig-van-Beethoven-Straße extended avenue name here")
        assert(NavFormatter.text(long).length <= 26) { NavFormatter.text(long) }
    }
}
