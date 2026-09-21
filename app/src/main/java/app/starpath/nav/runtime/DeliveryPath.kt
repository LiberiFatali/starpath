package app.starpath.nav.runtime

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build

/**
 * Which companion app StarPath delivers turn cards through.
 *
 * Exactly one companion app may be installed: the Zepp App (stable default)
 * or Gadgetbridge (extra path; PebbleKit alerts proven on Amazfit Active 2,
 * issue #2). Both installed — or neither — blocks all delivery until the
 * user keeps exactly one, because two feeders fight over the watch's single
 * Bluetooth link (reconnect loop observed Sep 2026).
 */
enum class DeliveryPath {
    ZEPP,
    GADGETBRIDGE,
    BLOCKED_BOTH,
    BLOCKED_NONE,
    ;

    val isDeliverable: Boolean
        get() = this == ZEPP || this == GADGETBRIDGE

    fun storageKey(): String =
        when (this) {
            ZEPP -> "zepp"
            GADGETBRIDGE -> "gadgetbridge"
            BLOCKED_BOTH -> "blocked_both"
            BLOCKED_NONE -> "blocked_none"
        }
}

object DeliveryPaths {
    const val ZEPP_PACKAGE = "com.huami.watch.hmwatchmanager"
    const val GADGETBRIDGE_PACKAGE = "nodomain.freeyourgadget.gadgetbridge"
    const val PREF_DELIVERY_PATH = "delivery_path"
    const val PREF_PEBBLE_ALWAYS_CONFIRMED = "pebble_always_confirmed"

    /**
     * Whether the 1-tap setup must prompt for Gadgetbridge's Pebble Messages
     * setting. Gadgetbridge drops our PebbleKit cards unless it is Always,
     * and its prefs are private to its own UID — so StarPath cannot probe
     * the value and relies on this manual confirmation instead.
     */
    fun shouldPromptPebble(path: DeliveryPath, pebbleConfirmed: Boolean): Boolean =
        path == DeliveryPath.GADGETBRIDGE && !pebbleConfirmed

    /**
     * Pure resolution over an install-probe, so the full matrix is JVM-tested.
     * Callers pass [DeliveryPaths.isInstalled]. The result is persisted
     * (see [loadOrResolve]) so the listener hot path never probes per post.
     */
    fun resolve(isInstalled: (String) -> Boolean): DeliveryPath {
        val zepp = isInstalled(ZEPP_PACKAGE)
        val gb = isInstalled(GADGETBRIDGE_PACKAGE)
        return when {
            zepp && gb -> DeliveryPath.BLOCKED_BOTH
            zepp -> DeliveryPath.ZEPP
            gb -> DeliveryPath.GADGETBRIDGE
            else -> DeliveryPath.BLOCKED_NONE
        }
    }

    /** Pure storage mapping (JVM-tested); null/unknown means "never chosen". */
    fun fromStorage(value: String?): DeliveryPath? =
        when (value) {
            DeliveryPath.ZEPP.storageKey() -> DeliveryPath.ZEPP
            DeliveryPath.GADGETBRIDGE.storageKey() -> DeliveryPath.GADGETBRIDGE
            DeliveryPath.BLOCKED_BOTH.storageKey() -> DeliveryPath.BLOCKED_BOTH
            DeliveryPath.BLOCKED_NONE.storageKey() -> DeliveryPath.BLOCKED_NONE
            else -> null
        }

    /**
     * Stored choice if present, else resolve once and persist. The choice is
     * made a single time (first run) and trusted afterwards; [refreshStored]
     * is the only other writer.
     */
    fun loadOrResolve(
        prefs: SharedPreferences,
        isInstalled: (String) -> Boolean,
    ): DeliveryPath {
        fromStorage(prefs.getString(PREF_DELIVERY_PATH, null))?.let { return it }
        return refreshStored(prefs, isInstalled)
    }

    /** Re-resolve unconditionally and persist (MainActivity open). */
    fun refreshStored(
        prefs: SharedPreferences,
        isInstalled: (String) -> Boolean,
    ): DeliveryPath =
        resolve(isInstalled).also {
            prefs.edit().putString(PREF_DELIVERY_PATH, it.storageKey()).apply()
        }

    /** Thin PackageManager probe (framework-bound, intentionally untested). */
    fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
}
