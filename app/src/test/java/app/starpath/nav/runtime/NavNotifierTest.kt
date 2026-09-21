package app.starpath.nav.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavNotifierTest {

    @Test
    fun `fresh install rebuilds channels once`() {
        assertTrue(NavNotifier.shouldRebuildChannels(0))
    }

    @Test
    fun `current version skips steady-state rebuild`() {
        assertFalse(NavNotifier.shouldRebuildChannels(NavNotifier.CHANNEL_VERSION))
    }

    @Test
    fun `stale version rebuilds exactly once`() {
        assertTrue(NavNotifier.shouldRebuildChannels(NavNotifier.CHANNEL_VERSION - 1))
    }
}
