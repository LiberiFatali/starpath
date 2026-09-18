package app.starpath.nav.runtime

import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class NavDedupTest {

    private fun update(
        maneuver: NavManeuver = NavManeuver.TURN_LEFT,
        street: String = "Nguyen Hue",
        state: NavState = NavState.ENROUTE,
        distanceText: String = "200 m",
        distanceMeters: Int? = 200,
        tripLine: String = "9 min",
    ) = NavUpdate(maneuver, distanceText, distanceMeters, street, tripLine, state)

    @Test
    fun `first post is never redundant`() {
        assertFalse(NavDedup.isRedundant(update(), null))
    }

    @Test
    fun `countdown tick with same direction and street is redundant`() {
        val last = update(distanceText = "200 m", distanceMeters = 200, tripLine = "9 min")
        val tick = update(distanceText = "180 m", distanceMeters = 180, tripLine = "8 min")
        assertTrue(NavDedup.isRedundant(tick, last))
    }

    @Test
    fun `street change posts`() {
        val last = update(street = "Nguyen Hue")
        assertFalse(NavDedup.isRedundant(update(street = "Le Loi"), last))
    }

    @Test
    fun `direction change posts`() {
        val last = update(maneuver = NavManeuver.TURN_LEFT)
        assertFalse(NavDedup.isRedundant(update(maneuver = NavManeuver.TURN_RIGHT), last))
    }

    @Test
    fun `canonically same turn family is redundant`() {
        val last = update(maneuver = NavManeuver.TURN_LEFT)
        assertTrue(NavDedup.isRedundant(update(maneuver = NavManeuver.SLIGHT_LEFT), last))
    }

    @Test
    fun `state change posts`() {
        val last = update(state = NavState.ENROUTE)
        assertFalse(NavDedup.isRedundant(update(state = NavState.REROUTING), last))
    }

    @Test
    fun `street normalization collapses spacing and case`() {
        assertEquals("ct5-đn4 / tòa nhà", NavDedup.normalizeStreet("CT5-ĐN4  /  Tòa Nhà"))
        val last = update(street = "CT5-ĐN4  / Tòa nhà CT5-ĐN4")
        assertTrue(NavDedup.isRedundant(update(street = "ct5-đn4 / tòa nhà ct5-đn4"), last))
    }

    @Test
    fun `both pin mirrors share one dest key`() {
        val left = update(maneuver = NavManeuver.DESTINATION, street = "CT5-ĐN4", distanceText = "", distanceMeters = null)
        val right = update(maneuver = NavManeuver.DESTINATION, street = "CT5-ĐN4  / Tòa nhà", distanceText = "", distanceMeters = null)
        // Same venue head via normalization is out of scope; identical streets dedup.
        assertTrue(NavDedup.isRedundant(left, left.copy()))
        assertFalse(NavDedup.isRedundant(right, left))
        assertEquals(NavManeuver.DESTINATION, left.maneuver.canonical())
        assertEquals("DEST", NavFormatter.title(left))
    }
}
