package app.starpath.coords

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocationTest {

    @Test
    fun `declared permissions include fine and coarse location`() {
        val permissions = CurrentLocation.permissions.toList()
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(2, permissions.size)
    }

    @Test
    fun `result found contains exact coordinates without cache`() {
        val result = CurrentLocation.Result.Found(41.40363, 2.17436)
        assertEquals(41.40363, result.latitude, 1e-6)
        assertEquals(2.17436, result.longitude, 1e-6)
    }
}
