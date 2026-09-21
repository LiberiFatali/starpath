package app.starpath.nav.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPathTest {

    private fun resolve(zepp: Boolean, gb: Boolean) =
        DeliveryPaths.resolve { pkg ->
            when (pkg) {
                DeliveryPaths.ZEPP_PACKAGE -> zepp
                DeliveryPaths.GADGETBRIDGE_PACKAGE -> gb
                else -> false
            }
        }

    @Test
    fun `zepp only delivers via zepp`() {
        assertEquals(DeliveryPath.ZEPP, resolve(zepp = true, gb = false))
    }

    @Test
    fun `gadgetbridge only delivers via gadgetbridge`() {
        assertEquals(DeliveryPath.GADGETBRIDGE, resolve(zepp = false, gb = true))
    }

    @Test
    fun `both installed blocks delivery`() {
        assertEquals(DeliveryPath.BLOCKED_BOTH, resolve(zepp = true, gb = true))
    }

    @Test
    fun `neither installed blocks delivery`() {
        assertEquals(DeliveryPath.BLOCKED_NONE, resolve(zepp = false, gb = false))
    }

    @Test
    fun `only zepp and gadgetbridge paths are deliverable`() {
        assertTrue(DeliveryPath.ZEPP.isDeliverable)
        assertTrue(DeliveryPath.GADGETBRIDGE.isDeliverable)
        assertFalse(DeliveryPath.BLOCKED_BOTH.isDeliverable)
        assertFalse(DeliveryPath.BLOCKED_NONE.isDeliverable)
    }

    @Test
    fun `unknown packages never resolve`() {
        assertEquals(DeliveryPath.BLOCKED_NONE, DeliveryPaths.resolve { false })
    }

    @Test
    fun `storage round-trips every path`() {
        for (path in DeliveryPath.values()) {
            assertEquals(path, DeliveryPaths.fromStorage(path.storageKey()))
        }
    }

    @Test
    fun `storage keys are stable literals`() {
        assertEquals("zepp", DeliveryPath.ZEPP.storageKey())
        assertEquals("gadgetbridge", DeliveryPath.GADGETBRIDGE.storageKey())
        assertEquals("blocked_both", DeliveryPath.BLOCKED_BOTH.storageKey())
        assertEquals("blocked_none", DeliveryPath.BLOCKED_NONE.storageKey())
    }

    @Test
    fun `unknown or missing storage means never chosen`() {
        assertNull(DeliveryPaths.fromStorage(null))
        assertNull(DeliveryPaths.fromStorage(""))
        assertNull(DeliveryPaths.fromStorage("zepp "))
    }

    @Test
    fun `pebble prompt fires only for unconfirmed gadgetbridge`() {
        assertTrue(DeliveryPaths.shouldPromptPebble(DeliveryPath.GADGETBRIDGE, false))
    }

    @Test
    fun `pebble prompt stays silent otherwise`() {
        assertFalse(DeliveryPaths.shouldPromptPebble(DeliveryPath.GADGETBRIDGE, true))
        assertFalse(DeliveryPaths.shouldPromptPebble(DeliveryPath.ZEPP, false))
        assertFalse(DeliveryPaths.shouldPromptPebble(DeliveryPath.BLOCKED_BOTH, false))
        assertFalse(DeliveryPaths.shouldPromptPebble(DeliveryPath.BLOCKED_NONE, false))
    }
}
