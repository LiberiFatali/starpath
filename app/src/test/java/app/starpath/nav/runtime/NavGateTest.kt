package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class NavGateTest {

    @Test
    fun `crowdsource prompt shape is dropped`() {
        // "CT5-DN4 · Has it closed? / Should this place be shown as closed"
        val junk = NavUpdate(
            maneuver = NavManeuver.UNKNOWN,
            street = "Should this place be shown as closed on Maps?",
            tripLine = "",
            state = NavState.ENROUTE,
        )
        assertFalse(NavGate.shouldForward(junk, appliedIcon = false))
    }

    @Test
    fun `real turns are forwarded`() {
        val turn = NavUpdate(NavManeuver.TURN_RIGHT, "P. Tran Van Can", "", NavState.ENROUTE)
        assertTrue(NavGate.shouldForward(turn, appliedIcon = false))
        val straight = NavUpdate(NavManeuver.STRAIGHT, "Highway", "", NavState.ENROUTE)
        assertTrue(NavGate.shouldForward(straight, appliedIcon = false))
    }

    @Test
    fun `icon-only turn with no text verdict is forwarded`() {
        val iconOnly = NavUpdate(
            maneuver = NavManeuver.TURN_LEFT,
            street = "P. Nguyen Co Thach",
            tripLine = "42 min · 21 km · 21:13 ETA",
            state = NavState.ENROUTE,
        )
        assertTrue(NavGate.shouldForward(iconOnly, appliedIcon = true))
    }

    @Test
    fun `text-only unknown is dropped, rerouting always forwards`() {
        // No icon verdict and no maneuver verb: not navigation, even with a
        // street-looking line (e.g. roundabout "Roundabout").
        val roundabout = NavUpdate(NavManeuver.UNKNOWN, "Roundabout", "", NavState.ENROUTE)
        assertFalse(NavGate.shouldForward(roundabout, appliedIcon = false))
        val rerouting = NavUpdate(NavManeuver.UNKNOWN, "Rerouting…", "", NavState.REROUTING)
        assertTrue(NavGate.shouldForward(rerouting, appliedIcon = false))
    }

    @Test
    fun `weak enroute clears stale rerouting, junk still dropped without history`() {
        // Field: no_text_rerouting — Maps recovered but the watch froze on
        // "… Rerouting" because the weak instruction frame was gated.
        val rerouting = NavUpdate(NavManeuver.UNKNOWN, "Rerouting…", "", NavState.REROUTING)
        val weakInstruction = NavUpdate(
            maneuver = NavManeuver.UNKNOWN,
            street = "CT37 Đ. Vành Đai 3",
            tripLine = "",
            state = NavState.ENROUTE,
        )
        assertTrue(NavGate.shouldForward(weakInstruction, appliedIcon = false, lastPosted = rerouting))
        // No history: same weak shape is still dropped (crowdsource guard).
        assertFalse(NavGate.shouldForward(weakInstruction, appliedIcon = false, lastPosted = null))
        // Blank street: not a live instruction, still dropped.
        assertFalse(
            NavGate.shouldForward(
                weakInstruction.copy(street = ""),
                appliedIcon = false,
                lastPosted = rerouting,
            ),
        )
    }
}
