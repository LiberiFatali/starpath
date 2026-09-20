package app.starpath.nav.parse

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
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
        assertEquals("Nguyen Hue", u.street)
        assertEquals(NavState.ENROUTE, u.state)
    }

    @Test
    fun `english turn keeps street, no next-turn distance parsed`() {
        val u = GMapsParser.parse(
            title = "Turn right after 1.2 km onto Le Loi",
            text = "Le Loi",
            bigText = null,
            textLines = emptyList(),
        )!!
        assertEquals(NavManeuver.TURN_RIGHT, u.maneuver)
        assertEquals("Le Loi", u.street)
    }

    @Test
    fun `roundabout exit and uturn`() {
        // Roundabouts and exits carry no side for the watch: UNKNOWN (?).
        // Guards stay ahead of the straight branch so "toward" in exit
        // strings must not fake a straight arrow.
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.parse("At the roundabout, take the 2nd exit in 500 m", null, null, emptyList())!!.maneuver,
        )
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.parse("Take exit 5 toward Long Bien in 1 km", null, null, emptyList())!!.maneuver,
        )
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.parse("Take the ramp onto Highway 1 in 500 m", null, null, emptyList())!!.maneuver,
        )
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.parse("Merge onto Highway 1 in 500 m", null, null, emptyList())!!.maneuver,
        )
        assertEquals(
            NavManeuver.UTURN,
            GMapsParser.parse("Make a U-turn in 100 m", null, null, emptyList())!!.maneuver,
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
    fun `rerouting detected`() {
        assertEquals(
            NavState.REROUTING,
            GMapsParser.parse("Rerouting…", null, null, emptyList())!!.state,
        )
        assertEquals(
            NavState.REROUTING,
            GMapsParser.parse("Recalculating route…", null, null, emptyList())!!.state,
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
        assertEquals("Nguyen Hue", u.street)
    }

    @Test
    fun `ahead does not force straight, compass headings defer to icon`() {
        // "head" substring must not match "ahead".
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.detectManeuver("Sharp curve ahead in 200 m"),
        )
        // Compass headings are orientation, not maneuvers (field:
        // maps_starpath_head_south) — UNKNOWN so the icon decides.
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.detectManeuver("Head north on Main St"),
        )
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.detectManeuver("Head south on Main St"),
        )
        // Explicit straight phrasing still counts.
        assertEquals(
            NavManeuver.STRAIGHT,
            GMapsParser.detectManeuver("Head straight on Main St"),
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
        assertEquals(NavManeuver.UNKNOWN, GMapsParser.detectManeuver("Take the ramp onto Highway 1"))
        assertEquals(NavManeuver.UNKNOWN, GMapsParser.detectManeuver("Merge onto Highway 1 in 500 m"))
        assertEquals(NavManeuver.UNKNOWN, GMapsParser.detectManeuver("Take exit 5 in 1 km"))
        assertEquals(NavManeuver.KEEP_LEFT, GMapsParser.detectManeuver("Keep left to stay on Highway 1"))
        assertEquals(NavManeuver.SLIGHT_RIGHT, GMapsParser.detectManeuver("Slight right in 100 m"))
        // Trip summary must not false-positive on "km left".
        assertEquals(NavManeuver.UNKNOWN, GMapsParser.detectManeuver("12 min · 3.2 km left"))
    }

    @Test
    fun `bare toward is unknown, defers to icon`() {
        // Field case turn_right_with_toward_text: "toward X" text with a
        // right-turn arrow. Text alone carries no direction — UNKNOWN so the
        // icon classifier decides, never a fake straight.
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.detectManeuver("toward P. Nguyen Co Thach"),
        )
        assertEquals(
            NavManeuver.UNKNOWN,
            GMapsParser.detectManeuver("Towards Nguyen Hue in 100 m"),
        )
        // Real verbs keep priority over "toward".
        assertEquals(
            NavManeuver.TURN_LEFT,
            GMapsParser.detectManeuver("Turn left toward P. Nguyen Co Thach"),
        )
        assertEquals(
            NavManeuver.UNKNOWN,
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
        // Remaining-trip info stays on the trip line only.
        assertEquals("Maps • 43 min • 15 km • 20:08 ETA", u.tripLine)
    }

    @Test
    fun `far trip remaining makes destination icon implausible`() {
        // Field regression roundabout_right: DEST lookalike with 3.5 km
        // remaining must suppress; true arrivals (150 m, 10 m) still apply.
        // Trip lines use non-breaking spaces ("3.5 km") — use them here.
        assertEquals(3500, GMapsParser.tripRemainingMeters("13 min · 3.5 km · 19:22 ETA"))
        assertEquals(false, GMapsParser.isPlausibleDestination("13 min · 3.5 km · 19:22 ETA"))
        assertEquals(600, GMapsParser.tripRemainingMeters("2 min · 600 m · 18:53 ETA"))
        assertEquals(false, GMapsParser.isPlausibleDestination("2 min · 600 m · 18:53 ETA"))
        assertEquals(150, GMapsParser.tripRemainingMeters("1 min · 150 m · 18:53 ETA"))
        assertEquals(true, GMapsParser.isPlausibleDestination("1 min · 150 m · 18:53 ETA"))
        assertEquals(10, GMapsParser.tripRemainingMeters("0 min · 10 m · 16:13 ETA"))
        assertEquals(true, GMapsParser.isPlausibleDestination("0 min · 10 m · 16:13 ETA"))
        // No distance info: never suppress.
        assertNull(GMapsParser.tripRemainingMeters(""))
        assertEquals(true, GMapsParser.isPlausibleDestination(""))
    }
}
