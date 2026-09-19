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
        alertManager = NavAlertManager()
    }

    @Test
    fun `evaluates maneuver change as alert`() {
        val update1 = NavUpdate(
            maneuver = NavManeuver.STRAIGHT,
            distanceText = "1.5 km",
            distanceMeters = 1500,
            street = "Main St",
            tripLine = "10 min",
            state = NavState.ENROUTE,
        )
        val d1 = alertManager.evaluate(update1)
        assertTrue(d1.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MANEUVER_CHANGED, d1.reason)

        // Maneuver changes to Turn Left
        val update2 = NavUpdate(
            maneuver = NavManeuver.TURN_LEFT,
            distanceText = "600 m",
            distanceMeters = 600,
            street = "Nguyen Hue",
            tripLine = "9 min",
            state = NavState.ENROUTE,
        )
        val d2 = alertManager.evaluate(update2)
        assertTrue(d2.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MANEUVER_CHANGED, d2.reason)
    }

    @Test
    fun `milestone thresholds trigger alerts`() {
        // Initial setup for TURN_LEFT at 600m (above every milestone)
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
        )

        // 400m is above the 200m milestone -> silent
        val d400 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "400 m", 400, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertFalse(d400.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d400.reason)

        // Cross 200m milestone -> alert
        val d200 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertTrue(d200.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d200.reason)

        // Minor change to 150m (no new milestone crossed) -> silent
        val d150 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "150 m", 150, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertFalse(d150.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d150.reason)

        // Cross 100m milestone -> alert
        val d100 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "100 m", 100, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertTrue(d100.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d100.reason)

        // Cross 50m milestone -> alert
        val d50 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "50 m", 50, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertTrue(d50.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d50.reason)
    }

    @Test
    fun `milestone jump still alerts on tighter bucket`() {
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
        )

        // Maps may jump 250m -> 180m without ever posting exactly 200m.
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "180 m", 180, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertTrue(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d.reason)
        assertEquals(200, alertManager.lastMilestone)
    }

    @Test
    fun `no repeat alert without new milestone`() {
        // First alert at 200m
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE),
        )

        // Same bucket re-posts stay silent without a time-based pulse
        val d1 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertFalse(d1.shouldAlert)

        val d2 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "180 m", 180, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertFalse(d2.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d2.reason)
    }

    @Test
    fun `straight cruising does not spam alerts`() {
        // Initial straight
        alertManager.evaluate(
            NavUpdate(NavManeuver.STRAIGHT, "2 km", 2000, "Highway", "", NavState.ENROUTE),
        )

        // Passing 200m while going straight does not trigger milestone alert
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.STRAIGHT, "200 m", 200, "Highway", "", NavState.ENROUTE),
        )
        assertFalse(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d.reason)
    }

    @Test
    fun `rerouting always triggers alert`() {
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.UNKNOWN, "", null, "Rerouting...", "", NavState.REROUTING),
        )
        assertTrue(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.REROUTING, d.reason)
    }

    @Test
    fun `left variants share one display mark and do not re-alert`() {
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
        )
        // Same canonical left, same distance: silent (no maneuver change).
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.SLIGHT_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
        )
        assertFalse(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d.reason)
    }

    @Test
    fun `reset clears tracking state`() {
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Street", "", NavState.ENROUTE),
        )
        alertManager.reset()
        assertEquals(null, alertManager.lastAlertedManeuver)
        assertEquals(null, alertManager.lastMilestone)
    }
}
