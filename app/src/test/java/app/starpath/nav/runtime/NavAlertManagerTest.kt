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
        alertManager = NavAlertManager(pulseIntervalMs = 18_000L)
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
        val d1 = alertManager.evaluate(update1, currentTimeMs = 1000L)
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
        val d2 = alertManager.evaluate(update2, currentTimeMs = 2000L)
        assertTrue(d2.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MANEUVER_CHANGED, d2.reason)
    }

    @Test
    fun `milestone thresholds trigger alerts`() {
        // Initial setup for TURN_LEFT at 600m (above 500m milestone)
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 1000L,
        )

        // Cross 500m milestone
        val d500 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "500 m", 500, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 5000L,
        )
        assertTrue(d500.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d500.reason)

        // Minor change to 400m (no new milestone crossed, inside 18s window) -> silent
        val d400 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "400 m", 400, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 10000L,
        )
        assertFalse(d400.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d400.reason)

        // Cross 200m milestone -> alert
        val d200 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 15000L,
        )
        assertTrue(d200.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d200.reason)

        // Cross 100m milestone -> alert
        val d100 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "100 m", 100, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 20000L,
        )
        assertTrue(d100.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d100.reason)

        // Cross 50m milestone -> alert
        val d50 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "50 m", 50, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 25000L,
        )
        assertTrue(d50.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.MILESTONE_CROSSED, d50.reason)
    }

    @Test
    fun `stay awake pulse triggers when waiting near turn`() {
        // First alert at 200m
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 1000L,
        )

        // 10 seconds later: screen might still be on or off, within 18s -> no pulse
        val d1 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 11000L,
        )
        assertFalse(d1.shouldAlert)

        // 19 seconds after last alert (1000 + 19000 = 20000) -> pulse triggers to re-wake watch
        val d2 = alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "180 m", 180, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 20000L,
        )
        assertTrue(d2.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.STAY_AWAKE_PULSE, d2.reason)
    }

    @Test
    fun `straight cruising does not spam alerts`() {
        // Initial straight
        alertManager.evaluate(
            NavUpdate(NavManeuver.STRAIGHT, "2 km", 2000, "Highway", "", NavState.ENROUTE),
            currentTimeMs = 1000L,
        )

        // Passing 500m while going straight does not trigger milestone alert
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.STRAIGHT, "500 m", 500, "Highway", "", NavState.ENROUTE),
            currentTimeMs = 30000L,
        )
        assertFalse(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d.reason)
    }

    @Test
    fun `rerouting always triggers alert`() {
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.UNKNOWN, "", null, "Rerouting...", "", NavState.REROUTING),
            currentTimeMs = 1000L,
        )
        assertTrue(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.REROUTING, d.reason)
    }

    @Test
    fun `left variants share one display mark and do not re-alert`() {
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 1000L,
        )
        // Same canonical left, same distance: silent (no maneuver change).
        val d = alertManager.evaluate(
            NavUpdate(NavManeuver.SLIGHT_LEFT, "600 m", 600, "Nguyen Hue", "", NavState.ENROUTE),
            currentTimeMs = 2000L,
        )
        assertFalse(d.shouldAlert)
        assertEquals(NavAlertManager.AlertReason.NONE, d.reason)
    }

    @Test
    fun `reset clears tracking state`() {
        alertManager.evaluate(
            NavUpdate(NavManeuver.TURN_LEFT, "200 m", 200, "Street", "", NavState.ENROUTE),
            currentTimeMs = 1000L,
        )
        alertManager.reset()
        assertEquals(null, alertManager.lastAlertedManeuver)
        assertEquals(null, alertManager.lastMilestone)
        assertEquals(0L, alertManager.lastAlertTimeMs)
    }
}
