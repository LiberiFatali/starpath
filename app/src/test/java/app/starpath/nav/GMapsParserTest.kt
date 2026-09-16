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
    fun `maneuver found when bigText holds trip summary, not instruction`() {
        // Regression: parser used to check only lines.first() (bigText),
        // so a trip summary hid the real turn in title.
        val u = GMapsParser.parse(
            title = "Turn left in 200 m onto Nguyen Hue",
            text = "Nguyen Hue",
            bigText = "12 min · 3.2 km · 5:30 PM",
            textLines = emptyList(),
        )!!
        assertEquals(NavManeuver.TURN_LEFT, u.maneuver)
        assertEquals("200 m", u.distanceText)
    }

    @Test
    fun `ahead does not force straight`() {
        // "head" substring must not match "ahead".
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.detectManeuver("Sharp curve ahead in 200 m"),
        )
        assertEquals(
            NavManeuver.STRAIGHT,
            GMapsParser.detectManeuver("Head north on Main St"),
        )
        assertEquals(
            NavManeuver.STRAIGHT,
            GMapsParser.detectManeuver("Continue on Nguyen Hue for 2 km"),
        )
    }

    @Test
    fun `expanded maneuver vocabulary`() {
        assertEquals(NavManeuver.SLIGHT_LEFT, GMapsParser.detectManeuver("Bear left in 100 m"))
        assertEquals(NavManeuver.SLIGHT_RIGHT, GMapsParser.detectManeuver("Bear right onto ramp"))
        assertEquals(NavManeuver.EXIT, GMapsParser.detectManeuver("Take the ramp onto Highway 1"))
        assertEquals(NavManeuver.EXIT, GMapsParser.detectManeuver("Merge onto Highway 1 in 500 m"))
        assertEquals(NavManeuver.EXIT, GMapsParser.detectManeuver("Take exit 5 in 1 km"))
        assertEquals(NavManeuver.KEEP_LEFT, GMapsParser.detectManeuver("Giữ làn trái"))
        assertEquals(NavManeuver.SLIGHT_RIGHT, GMapsParser.detectManeuver("Chếch phải"))
        // Trip summary must not false-positive on "km left".
        assertEquals(NavManeuver.UNKNOWN, GMapsParser.detectManeuver("12 min · 3.2 km left"))
    }

    @Test
    fun `bare toward is straight but never shadows verbs`() {
        // Screenshot case: Maps posts "toward X" with a straight arrow.
        assertEquals(
            NavManeuver.STRAIGHT,
            GMapsParser.detectManeuver("toward P. Nguyen Co Thach"),
        )
        assertEquals(
            NavManeuver.STRAIGHT,
            GMapsParser.detectManeuver("Towards Nguyen Hue in 100 m"),
        )
        // Real verbs keep priority over "toward".
        assertEquals(
            NavManeuver.TURN_LEFT,
            GMapsParser.detectManeuver("Turn left toward P. Nguyen Co Thach"),
        )
        assertEquals(
            NavManeuver.EXIT,
            GMapsParser.detectManeuver("Take exit 5 toward Long Bien"),
        )
        assertEquals(
            NavManeuver.KEEP_RIGHT,
            GMapsParser.detectManeuver("Keep right toward the ferry"),
        )
    }

    @Test
    fun `subText feeds trip line only, never distance`() {
        val u = GMapsParser.parse(
            title = "P. Tran Van Can",
            text = null,
            bigText = null,
            textLines = emptyList(),
            subText = "Maps • 43 min • 15 km • 20:08 ETA",
        )!!
        assertEquals(NavManeuver.UNKNOWN, u.maneuver)
        // Remaining-trip distance must not masquerade as turn distance.
        assertEquals("", u.distanceText)
        assertEquals("Maps • 43 min • 15 km • 20:08 ETA", u.tripLine)
    }

    @Test
    fun `unknown formatter shows question mark`() {
        val u = NavUpdate(NavManeuver.UNKNOWN, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE)
        assertEquals("? 200 m", NavFormatter.title(u))
    }
}
