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
        val left = NavUpdate(NavManeuver.TURN_LEFT, "300 m", 300, "Street", "", NavState.ENROUTE)
        assertEquals("300 m ◀◀", NavFormatter.title(left))

        val right = NavUpdate(NavManeuver.TURN_RIGHT, "1.5 km", 1500, "Avenue", "", NavState.ENROUTE)
        assertEquals("1.5 km ▶▶", NavFormatter.title(right))

        val straight = NavUpdate(NavManeuver.STRAIGHT, "5 km", 5000, "Highway", "", NavState.ENROUTE)
        assertEquals("5 km ▲▲", NavFormatter.title(straight))

        val dest = NavUpdate(NavManeuver.DESTINATION, "", 0, "Home", "", NavState.ENROUTE)
        assertEquals("DEST", NavFormatter.title(dest))

        // Arrival with Maps-borrowed distance stays mark-only, so "600 m"
        // doesn't echo line 2 ("Drive 2 min (600 m)…") and line 3.
        val destBorrowed =
            NavUpdate(
                NavManeuver.DESTINATION,
                "600 m",
                600,
                "Drive 2 min (600 m) to Chill 'n Feel Coffee",
                "Drive 2 min (600 m) to Chill 'n Feel Coffee",
                NavState.ENROUTE,
            )
        assertEquals("DEST", NavFormatter.title(destBorrowed))
        assertEquals("DEST", NavFormatter.title(destBorrowed, useAscii = true))
        // Line 3 keeps distance for alignment with en-route cards.
        assertEquals("600 m · 2 min", NavFormatter.subText(destBorrowed))

        val reroute = NavUpdate(NavManeuver.UNKNOWN, "", null, "", "", NavState.REROUTING)
        assertEquals("… Rerouting", NavFormatter.title(reroute))
    }

    @Test
    fun `display collapses to left right straight unknown`() {
        // Left/right families share one mark; sideless maneuvers render as ?.
        assertEquals(
            "50 m ◀◀",
            NavFormatter.title(NavUpdate(NavManeuver.SLIGHT_LEFT, "50 m", 50, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "50 m ◀◀",
            NavFormatter.title(NavUpdate(NavManeuver.SHARP_LEFT, "50 m", 50, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "50 m ◀◀",
            NavFormatter.title(NavUpdate(NavManeuver.KEEP_LEFT, "50 m", 50, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "50 m ▶▶",
            NavFormatter.title(NavUpdate(NavManeuver.KEEP_RIGHT, "50 m", 50, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "100 m ?",
            NavFormatter.title(NavUpdate(NavManeuver.UTURN, "100 m", 100, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "500 m ?",
            NavFormatter.title(NavUpdate(NavManeuver.UNKNOWN, "500 m", 500, "St", "", NavState.ENROUTE)),
        )
        assertEquals(
            "200 m ?",
            NavFormatter.title(NavUpdate(NavManeuver.UNKNOWN, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE)),
        )
        assertEquals(
            "1 km ?",
            NavFormatter.title(NavUpdate(NavManeuver.UNKNOWN, "1 km", 1000, "St", "", NavState.ENROUTE)),
        )
    }

    @Test
    fun `ascii mode uses essential marks only`() {
        fun asciiTitle(maneuver: NavManeuver, distance: String) =
            NavFormatter.title(
                NavUpdate(maneuver, distance, null, "St", "", NavState.ENROUTE),
                useAscii = true,
            )
        assertEquals("200 m <-", asciiTitle(NavManeuver.TURN_LEFT, "200 m"))
        assertEquals("200 m <-", asciiTitle(NavManeuver.SLIGHT_LEFT, "200 m"))
        assertEquals("200 m ->", asciiTitle(NavManeuver.TURN_RIGHT, "200 m"))
        assertEquals("200 m ->", asciiTitle(NavManeuver.SHARP_RIGHT, "200 m"))
        assertEquals("2 km ^", asciiTitle(NavManeuver.STRAIGHT, "2 km"))
        assertEquals("?", asciiTitle(NavManeuver.UTURN, ""))
        assertEquals("?", asciiTitle(NavManeuver.UNKNOWN, ""))
        assertEquals("DEST", asciiTitle(NavManeuver.DESTINATION, ""))
        assertEquals("200 m ?", asciiTitle(NavManeuver.UNKNOWN, "200 m"))
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
    fun `subText prefixes destination remaining and drops ETA clock`() {
        fun sub(trip: String) =
            NavFormatter.subText(NavUpdate(NavManeuver.TURN_RIGHT, "260 m", 260, "St", trip, NavState.ENROUTE))

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
        val dest = NavUpdate(NavManeuver.DESTINATION, "", 0, "Home", "6 min · 450 m", NavState.ENROUTE)
        assertEquals("450 m · 6 min", NavFormatter.subText(dest))

        val reroute = NavUpdate(NavManeuver.UNKNOWN, "", null, "", "", NavState.REROUTING)
        assertEquals("", NavFormatter.subText(reroute))
    }

    @Test
    fun `subText caps at 48 characters including prefix`() {
        val hugeTrip = "9".repeat(60) + " min · 450 m"
        val hugeUpdate = NavUpdate(NavManeuver.TURN_LEFT, "100 m", 100, "St", hugeTrip, NavState.ENROUTE)
        val sub = NavFormatter.subText(hugeUpdate)
        assertEquals(48, sub.length)
        assertTrue(sub.startsWith("DEST "))
    }
}
