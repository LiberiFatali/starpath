package app.starpath.nav

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Low-profile foreground service that keeps the process (and thus
 * [StarPathListener]) alive while navigating in Google Maps with the screen off.
 *
 * Automatically started when Google Maps navigation begins, and automatically
 * stopped when navigation ends or when the user stops/exits.
 */
class KeepAliveService : Service() {

    companion object {
        const val ACTION_START = "app.starpath.action.START"
        const val ACTION_STOP = "app.starpath.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore if background start restrictions prevent start
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            NavNotifier(this).cancel()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundCompat()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
