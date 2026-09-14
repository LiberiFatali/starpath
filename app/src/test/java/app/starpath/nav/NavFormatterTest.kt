package app.starpath.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavFormatterTest {

    @Test
    fun `formats titles for various maneuvers`() {
        val left = NavUpdate(NavManeuver.TURN_LEFT, "300 m", 300, "Street", "", NavState.ENROUTE)
        assertEquals("◀◀ 300 m", NavFormatter.title(left))

        val right = NavUpdate(NavManeuver.TURN_RIGHT, "1.5 km", 1500, "Avenue", "", NavState.ENROUTE)
        assertEquals("▶▶ 1.5 km", NavFormatter.title(right))

        val straight = NavUpdate(NavManeuver.STRAIGHT, "5 km", 5000, "Highway", "", NavState.ENROUTE)
        assertEquals("▲▲ 5 km", NavFormatter.title(straight))

        val dest = NavUpdate(NavManeuver.DESTINATION, "", 0, "Home", "", NavState.ENROUTE)
        assertEquals("🏁", NavFormatter.title(dest))

        val reroute = NavUpdate(NavManeuver.UNKNOWN, "", null, "", "", NavState.REROUTING)
        assertEquals("… Rerouting", NavFormatter.title(reroute))
    }

    @Test
    fun `text truncation caps length for watch readability`() {
        val shortStreet = NavUpdate(NavManeuver.TURN_LEFT, "100 m", 100, "Short St", "", NavState.ENROUTE)
        assertEquals("Short St", NavFormatter.text(shortStreet))

        val longStreet = NavUpdate(
            NavManeuver.TURN_LEFT,
            "100 m",
            100,
            "A Very Long Street Name That Would Overflow The Watch Display",
            "",
            NavState.ENROUTE,
        )
        val truncated = NavFormatter.text(longStreet)
        assertTrue(truncated.length <= 26)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun `subText caps at 48 characters`() {
        val normal = NavUpdate(NavManeuver.TURN_LEFT, "100 m", 100, "St", "12 min · 3.2 km", NavState.ENROUTE)
        assertEquals("12 min · 3.2 km", NavFormatter.subText(normal))

        val longTrip = "A".repeat(100)
        val longUpdate = NavUpdate(NavManeuver.TURN_LEFT, "100 m", 100, "St", longTrip, NavState.ENROUTE)
        assertEquals(48, NavFormatter.subText(longUpdate).length)
    }

    @Test
    fun `shouldRepost behaves accurately`() {
        val u1 = NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "10 min", NavState.ENROUTE)
        val card1 = NavFormatter.toCard(u1)

        // When old is null -> should repost
        assertTrue(NavFormatter.shouldRepost(null, u1))

        // Same content -> should not repost
        assertFalse(NavFormatter.shouldRepost(card1, u1))

        // Distance changed -> should repost
        val u2 = u1.copy(distanceText = "150 m", distanceMeters = 150)
        assertTrue(NavFormatter.shouldRepost(card1, u2))

        // Street changed -> should repost
        val u3 = u1.copy(street = "Le Loi")
        assertTrue(NavFormatter.shouldRepost(card1, u3))
    }
}
