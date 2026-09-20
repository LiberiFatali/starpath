package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


class NavAlertManagerTest {

    private lateinit var alertManager: NavAlertManager

    @Before
    fun setUp() {
        alertManager = NavAlertManager(staleIntervalMs = 30_000L)
    }

    private fun update(
        maneuver: NavManeuver = NavManeuver.TURN_LEFT,
        street: String = "Nguyen Hue",
        tripLine: String = "9 min",
        state: NavState = NavState.ENROUTE,
    ) = NavUpdate(maneuver, street, tripLine, state)

    @Test
    fun `evaluates maneuver change as alert`() {
        val d1 = alertManager.evaluate(
            NavUpdate(NavManeuver.STRAIGHT, "Main St", "10 min", NavState.ENROUTE),
            currentTimeMs = 1000L,
        )
        assertTrue(d1.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MANEUVER_CHANGED, d1.reason)

        // Maneuver changes to Turn Left
        val d2 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "Nguyen Hue", "9 min", NavState.ENROUTE),
            currentTimeMs = 2000L,
        )
        assertTrue(d2.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MANEUVER_CHANGED, d2.reason)
    }

    @Test
    fun `unchanged instruction stays silent inside stale window`() {
        alertManager.evaluate(update(), currentTimeMs = 1000L)

        // Identical re-post 10s later: silent.
        val d = alertManager.evaluate(update(), currentTimeMs = 11_000L)
        assertFalse(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d.reason)
    }

    @Test
    fun `stale reminder fires after 30s without change`() {
        alertManager.evaluate(update(), currentTimeMs = 1000L)

        val d = alertManager.evaluate(update(), currentTimeMs = 31_000L)
        assertTrue(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.STALE_REMINDER, d.reason)
    }

    @Test
    fun `stale reminder repeats every 30s`() {
        alertManager.evaluate(update(), currentTimeMs = 1000L)

        // First reminder at +30s resets the clock...
        val d1 = alertManager.evaluate(update(), currentTimeMs = 31_000L)
        assertTrue(d1.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.STALE_REMINDER, d1.reason)

        // ...so 10s after the reminder is silent again...
        val d2 = alertManager.evaluate(update(), currentTimeMs = 41_000L)
        assertFalse(d2.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d2.reason)

        // ...and the next reminder fires 30s after the previous one.
        val d3 = alertManager.evaluate(update(), currentTimeMs = 61_000L)
        assertTrue(d3.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.STALE_REMINDER, d3.reason)
    }

    @Test
    fun `stale reminder applies while cruising straight`() {
        alertManager.evaluate(
            update(maneuver = NavManeuver.STRAIGHT, street = "Highway"),
            currentTimeMs = 1000L,
        )

        val d = alertManager.evaluate(
            update(maneuver = NavManeuver.STRAIGHT, street = "Highway"),
            currentTimeMs = 31_000L,
        )
        assertTrue(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.STALE_REMINDER, d.reason)
    }

    @Test
    fun `rerouting always triggers alert`() {
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.UNKNOWN, "Rerouting...", "", NavState.REROUTING),
            currentTimeMs = 1000L,
        )
        assertTrue(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.REROUTING, d.reason)
    }

    @Test
    fun `left variants share one display mark and do not re-alert`() {
        alertManager.evaluate(
            update(maneuver = NavManeuver.TURN_LEFT),
            currentTimeMs = 1000L,
        )
        // Same canonical left: silent (no maneuver change, inside stale window).
        val d = alertManager.evaluate(
            update(maneuver = NavManeuver.SLIGHT_LEFT),
            currentTimeMs = 2000L,
        )
        assertFalse(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d.reason)
    }

    @Test
    fun `reset clears tracking state`() {
        alertManager.evaluate(update(), currentTimeMs = 1000L)
        alertManager.reset()
        assertEquals(null, alertManager.lastAlertedManeuver)
        assertEquals(0L, alertManager.lastAlertTimeMs)
    }
}
