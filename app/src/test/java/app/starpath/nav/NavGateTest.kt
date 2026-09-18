package app.starpath.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGateTest {

    @Test
    fun `crowdsource prompt shape is dropped`() {
        // "CT5-DN4 · Has it closed? / Should this place be shown as closed"
        val junk = NavUpdate(
            maneuver = NavManeuver.UNKNOWN,
            distanceText = "",
            distanceMeters = null,
            street = "Should this place be shown as closed on Maps?",
            tripLine = "",
            state = NavState.ENROUTE,
        )
        assertFalse(NavGate.shouldForward(junk, appliedIcon = false))
    }

    @Test
    fun `real turns are forwarded`() {
        val turn = NavUpdate(NavManeuver.TURN_RIGHT, "260 m", 260, "P. Tran Van Can", "", NavState.ENROUTE)
        assertTrue(NavGate.shouldForward(turn, appliedIcon = false))
        val straight = NavUpdate(NavManeuver.STRAIGHT, "5 km", 5000, "Highway", "", NavState.ENROUTE)
        assertTrue(NavGate.shouldForward(straight, appliedIcon = false))
    }

    @Test
    fun `icon-only turn with no text verdict is forwarded`() {
        val iconOnly = NavUpdate(
            maneuver = NavManeuver.TURN_LEFT,
            distanceText = "",
            distanceMeters = null,
            street = "P. Nguyen Co Thach",
            tripLine = "42 min · 21 km · 21:13 ETA",
            state = NavState.ENROUTE,
        )
        assertTrue(NavGate.shouldForward(iconOnly, appliedIcon = true))
    }

    @Test
    fun `unknown with distance is navigation, rerouting always forwards`() {
        val roundabout = NavUpdate(NavManeuver.UNKNOWN, "500 m", 500, "Roundabout", "", NavState.ENROUTE)
        assertTrue(NavGate.shouldForward(roundabout, appliedIcon = false))
        val rerouting = NavUpdate(NavManeuver.UNKNOWN, "", null, "Rerouting…", "", NavState.REROUTING)
        assertTrue(NavGate.shouldForward(rerouting, appliedIcon = false))
    }
}
