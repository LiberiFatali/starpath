package app.starpath.nav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Re-posts the parsed navigation state as StarPath's own notification.
 * This is the card Zepp App Alerts forwards over BLE to the watch.
 */
class NavNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "starpath_live"
        const val NOTIF_ID = 1001
        const val KEEPALIVE_CHANNEL_ID = "starpath_keepalive"
        const val KEEPALIVE_ID = 1002
    }

    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "StarPath live directions",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Turn-by-turn cards forwarded to your watch"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                KEEPALIVE_CHANNEL_ID, "StarPath listener",
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = "Keeps navigation listening alive" }
        )
    }

    /**
     * @param alert true when the maneuver changed or a distance milestone was reached:
     *   the re-post vibrates the phone + wakes the watch display.
     *   Distance/ETA refreshes pass false (silent update).
     */
    fun post(card: NavFormatter.Card, alert: Boolean) {
        val arrowBitmap = ArrowBitmapGenerator.createArrowBitmap(card.maneuver)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setLargeIcon(arrowBitmap)
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

    fun keepAliveNotification(): Notification =
        NotificationCompat.Builder(context, KEEPALIVE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("StarPath listening")
            .setContentText("Start navigation in Google Maps")
            .setOngoing(true)
            .setSilent(true)
            .build()
}
