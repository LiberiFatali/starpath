package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class NavFormatterTest {

    @Test
    fun `formats titles for various maneuvers`() {
        // Titles are mark-only: next-turn distance is not parsed.
        val left = NavUpdate(NavManeuver.TURN_LEFT, "Street", "", NavState.ENROUTE)
        assertEquals("◀◀", NavFormatter.title(left))

        val right = NavUpdate(NavManeuver.TURN_RIGHT, "Avenue", "", NavState.ENROUTE)
        assertEquals("▶▶", NavFormatter.title(right))

        val straight = NavUpdate(NavManeuver.STRAIGHT, "Highway", "", NavState.ENROUTE)
        assertEquals("▲▲", NavFormatter.title(straight))

        val dest = NavUpdate(NavManeuver.DESTINATION, "Home", "", NavState.ENROUTE)
        assertEquals("DEST", NavFormatter.title(dest))

        val unknown = NavUpdate(NavManeuver.UNKNOWN, "Nguyen Hue", "", NavState.ENROUTE)
        assertEquals("?", NavFormatter.title(unknown))

        val reroute = NavUpdate(NavManeuver.UNKNOWN, "", "", NavState.REROUTING)
        assertEquals("… Rerouting", NavFormatter.title(reroute))
    }

    @Test
    fun `display collapses to left right straight unknown`() {
        // Left/right families share one mark; sideless maneuvers render as ?.
        assertEquals(
            "◀◀",
            NavFormatter.title(NavUpdate(NavManeuver.SLIGHT_LEFT, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "◀◀",
            NavFormatter.title(NavUpdate(NavManeuver.SHARP_LEFT, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "◀◀",
            NavFormatter.title(NavUpdate(NavManeuver.KEEP_LEFT, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "▶▶",
            NavFormatter.title(NavUpdate(NavManeuver.KEEP_RIGHT, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "?",
            NavFormatter.title(NavUpdate(NavManeuver.UTURN, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "?",
            NavFormatter.title(NavUpdate(NavManeuver.UNKNOWN, "St", "", NavState.ENROUTE)),
        )
    }

    @Test
    fun `ascii mode uses essential marks only`() {
        fun asciiTitle(maneuver: NavManeuver) =
            NavFormatter.title(
                NavUpdate(maneuver, "St", "", NavState.ENROUTE),
                useAscii = true,
            )
        assertEquals("<-", asciiTitle(NavManeuver.TURN_LEFT))
        assertEquals("<-", asciiTitle(NavManeuver.SLIGHT_LEFT))
        assertEquals("->", asciiTitle(NavManeuver.TURN_RIGHT))
        assertEquals("->", asciiTitle(NavManeuver.SHARP_RIGHT))
        assertEquals("^", asciiTitle(NavManeuver.STRAIGHT))
        assertEquals("?", asciiTitle(NavManeuver.UTURN))
        assertEquals("?", asciiTitle(NavManeuver.UNKNOWN))
        assertEquals("DEST", asciiTitle(NavManeuver.DESTINATION))
    }

    @Test
    fun `text truncation caps length for watch readability`() {
        val shortStreet = NavUpdate(NavManeuver.TURN_LEFT, "Short St", "", NavState.ENROUTE)
        assertEquals("Short St", NavFormatter.text(shortStreet))

        val longStreet = NavUpdate(
            NavManeuver.TURN_LEFT,
            "A Very Long Street Name That Would Overflow The Watch Display",
            "",
            NavState.ENROUTE,
        )
        val truncated = NavFormatter.text(longStreet)
        assertTrue(truncated.length <= 26)
        assertTrue(truncated.endsWith("…"))
    }

    @Test
    fun `subText prefixes destination remaining and drops ETA clock`() {
        fun sub(trip: String) =
            NavFormatter.subText(NavUpdate(NavManeuver.TURN_RIGHT, "St", trip, NavState.ENROUTE))

        assertEquals("DEST 3.2 km · 12 min", sub("12 min · 3.2 km"))
        // Field case: remaining trip header, ETA clock dropped.
        assertEquals("DEST 450 m · 6 min", sub("6 min · 450 m · 22:12 ETA"))
        assertEquals("DEST 20 m · 0 min", sub("0 min · 20 m · 16:21 ETA"))
        assertEquals("DEST 15 km · 43 min", sub("Maps • 43 min • 15 km • 20:08 ETA"))
        // Street fallback or ETA-only carries nothing the watch needs.
        assertEquals("", sub("P. Tran Van Can"))
        assertEquals("", sub(""))
    }

    @Test
    fun `subText skips prefix on arrival and rerouting`() {
        val dest = NavUpdate(NavManeuver.DESTINATION, "Home", "6 min · 450 m", NavState.ENROUTE)
        assertEquals("450 m · 6 min", NavFormatter.subText(dest))

        val reroute = NavUpdate(NavManeuver.UNKNOWN, "", "", NavState.REROUTING)
        assertEquals("", NavFormatter.subText(reroute))
    }

    @Test
    fun `subText caps at 48 characters including prefix`() {
        val hugeTrip = "9".repeat(60) + " min · 450 m"
        val hugeUpdate = NavUpdate(NavManeuver.TURN_LEFT, "St", hugeTrip, NavState.ENROUTE)
        val sub = NavFormatter.subText(hugeUpdate)
        assertEquals(48, sub.length)
        assertTrue(sub.startsWith("DEST "))
    }
}
