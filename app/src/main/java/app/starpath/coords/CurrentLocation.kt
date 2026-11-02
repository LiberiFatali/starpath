package app.starpath.coords

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Live precise position from Android's GPS hardware.
 * Uses simple logic: live precise fix or no location obtained at all. No cache.
 */
object CurrentLocation {

    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    sealed interface Result {
        data class Found(val latitude: Double, val longitude: Double) : Result
        data object Off : Result
        data object NoFix : Result
        data object Denied : Result
    }

    private const val TIMEOUT_MS = 20_000L

    /** Requires ACCESS_FINE_LOCATION for precise coordinates. */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Requests a live precise GPS fix without reading any cached positions.
     * Delivers [Result.Found] on the first live fix, or [Result.NoFix] if
     * no fix is acquired within [timeoutMs].
     */
    @SuppressLint("MissingPermission")
    fun get(
        context: Context,
        timeoutMs: Long = TIMEOUT_MS,
        callback: (Result) -> Unit,
    ): CancellationSignal? {
        if (!hasPermission(context)) {
            callback(Result.Denied)
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null ||
            !LocationManagerCompat.isLocationEnabled(manager) ||
            !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        ) {
            callback(Result.Off)
            return null
        }

        val main = Handler(Looper.getMainLooper())
        val signal = CancellationSignal()
        var delivered = false
        lateinit var listener: LocationListener
        lateinit var timeoutRunnable: Runnable

        fun cleanup() {
            main.removeCallbacks(timeoutRunnable)
            try {
                manager.removeUpdates(listener)
            } catch (_: Exception) {
                // Provider may have been disabled concurrently
            }
        }

        fun deliver(result: Result) {
            if (delivered) return
            delivered = true
            cleanup()
            if (!signal.isCanceled) signal.cancel()
            callback(result)
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                deliver(Result.Found(location.latitude, location.longitude))
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    deliver(Result.Off)
                }
            }
        }

        timeoutRunnable = Runnable { deliver(Result.NoFix) }
        main.postDelayed(timeoutRunnable, timeoutMs)

        signal.setOnCancelListener {
            cleanup()
        }

        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            deliver(Result.Denied)
        } catch (_: IllegalArgumentException) {
            deliver(Result.Off)
        }

        return signal
    }
}
