package app.starpath.nav

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Low-profile foreground service that keeps the process (and thus
 * [StarPathListener]) alive while you ride with the screen off.
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notifier = NavNotifier(this).also { it.ensureChannels() }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NavNotifier.KEEPALIVE_ID,
                notifier.keepAliveNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NavNotifier.KEEPALIVE_ID, notifier.keepAliveNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null
}
