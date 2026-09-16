package app.starpath.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.starpath.BuildConfig

/**
 * Intercepts the Google Maps navigation notification, parses it, and
 * re-posts it as StarPath's glanceable card (which Zepp forwards to the watch).
 */
class StarPathListener : NotificationListenerService() {

    private lateinit var notifier: NavNotifier
    private val alertManager = NavAlertManager()
    private var lastCard: NavFormatter.Card? = null
    private var mapsActive = false

    override fun onCreate() {
        super.onCreate()
        notifier = NavNotifier(this).also { it.ensureChannels() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != GMapsParser.MAPS_PACKAGE) return
        if (!sbn.notification.flags.hasFlag(Notification.FLAG_ONGOING_EVENT)) {
            // Maps posts navigation as ongoing; ignore transient Maps shares.
            if (sbn.notification.extras.getString(Notification.EXTRA_TITLE).isNullOrBlank()) return
        }
        val extras = sbn.notification.extras
        if (BuildConfig.DEBUG) {
            Log.d(
                "StarPath",
                "maps posted title=${extras.getString(Notification.EXTRA_TITLE)} " +
                    "text=${extras.getCharSequence(Notification.EXTRA_TEXT)} " +
                    "bigText=${extras.getCharSequence(Notification.EXTRA_BIG_TEXT)} " +
                    "lines=${extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.toList()}",
            )
        }
        // Prefer the true instruction from Maps' custom RemoteViews
        // (nav_description/nav_title); extras are only a degraded fallback.
        val update = MapsRemoteParser.parseUpdate(sbn, this)
            ?: GMapsParser.parse(
                title = extras.getString(Notification.EXTRA_TITLE),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                    ?.map { it.toString() }.orEmpty(),
            ) ?: return

        if (!mapsActive) {
            mapsActive = true
            KeepAliveService.start(this)
        }

        val alertDecision = alertManager.evaluate(update)
        val contentChanged = NavFormatter.shouldRepost(lastCard, update)

        // Re-post if the content changed or if we need to re-alert / wake the watch screen
        if (!contentChanged && !alertDecision.shouldAlert) return

        val card = NavFormatter.toCard(update)
        lastCard = card
        notifier.post(card, alert = alertDecision.shouldAlert)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != GMapsParser.MAPS_PACKAGE || !mapsActive) return
        // Maps cleared its navigation session -> clear our card too.
        val stillThere = try {
            activeNotifications?.any {
                it != null &&
                    it.packageName == GMapsParser.MAPS_PACKAGE &&
                    it.notification.flags.hasFlag(Notification.FLAG_ONGOING_EVENT)
            } ?: false
        } catch (_: Exception) { true }
        if (!stillThere) {
            mapsActive = false
            lastCard = null
            alertManager.reset()
            notifier.cancel()
            KeepAliveService.stop(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        KeepAliveService.stop(this)
    }

    private fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag
}
