package app.starpath.nav.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.starpath.ui.MainActivity
/**
 * Re-posts the parsed navigation state as StarPath's own notification.
 * This is the card the companion app (Zepp App Alerts or Gadgetbridge
 * notification mirroring) forwards over BLE to the watch.
 */
class NavNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "starpath_live"
        const val NOTIF_ID = 1001
        const val KEEPALIVE_CHANNEL_ID = "starpath_keepalive"
        const val KEEPALIVE_ID = 1002
        const val SETUP_CHANNEL_ID = "starpath_setup"
        const val SETUP_NOTIF_ID = 1003
        const val PREF_CHANNEL_VERSION = "notif_channel_version"

        /**
         * Bump when any channel config below changes: existing installs get
         * exactly one delete + re-create (channels are immutable otherwise).
         */
        const val CHANNEL_VERSION = 1

        /** Pure gate for [NavNotifier.ensureChannels] (JVM-tested). */
        fun shouldRebuildChannels(storedVersion: Int): Boolean = storedVersion != CHANNEL_VERSION
    }

    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        // Channels persist after first creation, but a cold foreground-service
        // start (KeepAliveService.onCreate) pays every millisecond of this
        // method against the ~5s FGS timeout (RemoteServiceException history,
        // Sep 2026). So rebuild only when the config version changes; the
        // steady state is a single prefs read.
        val prefs = context.getSharedPreferences(NavFormatter.PREFS_FILE, Context.MODE_PRIVATE)
        if (!shouldRebuildChannels(prefs.getInt(PREF_CHANNEL_VERSION, 0))) return
        // Channels are immutable after first install: delete + re-create so
        // existing installs pick up the muted config below. Same ID keeps
        // Zepp's per-app selection intact.
        manager.deleteNotificationChannel(CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "StarPath live directions",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Turn-by-turn cards forwarded to your watch"
                // Silent phone card: Google Maps already alerts on the phone.
                // Importance stays HIGH so Zepp keeps waking the watch;
                // the phone itself never sounds or vibrates.
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                KEEPALIVE_CHANNEL_ID, "StarPath listener",
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = "Keeps navigation listening alive" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SETUP_CHANNEL_ID, "StarPath setup problems",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "One-off notices when watch delivery is blocked"
                enableVibration(false)
                setSound(null, null)
            }
        )
        prefs.edit().putInt(PREF_CHANNEL_VERSION, CHANNEL_VERSION).apply()
    }

    /**
     * @param alert true when the maneuver changed or a distance milestone was reached:
     *   the re-post nudges Zepp to wake the watch display. The phone itself
     *   stays silent (muted channel + [alert]-gated [NotificationCompat.Builder.setSilent]).
     *   Distance/ETA refreshes pass false (silent update).
     */
    fun post(card: NavFormatter.Card, alert: Boolean) {
        // Text-only card: Zepp forwards title/text over BLE to the watch;
        // largeIcon bitmaps never reach the Amazfit Active 2, so no
        // outbound bitmap is attached (payload + allocation savings).
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(card.title)
            .setContentText(card.text)
            .setSubText(card.sub.takeIf { it.isNotBlank() })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(listOf(card.text, card.sub).filter { it.isNotBlank() }.joinToString("\n"))
            )
            .setTicker("${card.title}: ${card.text}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // DO NOT set ongoing(true) here!
            // Zepp's NotificationListenerService explicitly filters out all FLAG_ONGOING_EVENT notifications.
            .setOngoing(false)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .build()
        manager.notify(NOTIF_ID, notif)
    }

    fun cancel() = manager.cancel(NOTIF_ID)

    /**
     * One-shot setup notice for the blocked states ([DeliveryPath.BLOCKED_BOTH]
     * / [DeliveryPath.BLOCKED_NONE]): tells the user why nothing reaches the
     * watch. Deliberately quiet (muted DEFAULT channel) — a setup nag must
     * never buzz like a navigation alert. Tap opens MainActivity.
     */
    fun postBlockedNotice(path: DeliveryPath) {
        val text = when (path) {
            DeliveryPath.BLOCKED_BOTH ->
                "Both Zepp and Gadgetbridge are installed — keep only one so StarPath can start."
            DeliveryPath.BLOCKED_NONE ->
                "No watch app installed — install Zepp or Gadgetbridge so StarPath can start."
            else -> return
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, SETUP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("StarPath blocked")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setSilent(true)
            .build()
        manager.notify(SETUP_NOTIF_ID, notif)
    }

    fun cancelBlockedNotice() = manager.cancel(SETUP_NOTIF_ID)

    fun keepAliveNotification(): Notification {
        val stopIntent = Intent(context, KeepAliveService::class.java).apply {
            action = KeepAliveService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, KEEPALIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("StarPath active")
            .setContentText("Syncing Google Maps navigation to watch")
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
