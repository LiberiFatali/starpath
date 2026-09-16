package app.starpath.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Intercepts the Google Maps navigation notification, parses it, and
 * re-posts it as StarPath's glanceable card (which Zepp forwards to the watch).
 */
class StarPathListener : NotificationListenerService() {

    private lateinit var notifier: NavNotifier
    private val alertManager = NavAlertManager()
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
        // Lightweight pipeline: extras text first, largeIcon arrow pixels
        // when the text has no verb. Decision order: text verb > icon
        // moments > UNKNOWN (never fake-straight).
        // Decision order: text verb > icon match > UNKNOWN (never fake-straight).
        // The watch only shows text glyphs, so the icon verdict is rendered
        // as a glyph too — see IconClassifier KDoc.
        val outcome = MapsRemoteParser.parseOutcome(sbn, this)
        LastParse.store(outcome)
        val update = outcome.update ?: return

        if (!mapsActive) {
            mapsActive = true
            KeepAliveService.start(this)
        }

        val alertDecision = alertManager.evaluate(update)

        // Always re-post: Maps can change direction faster than the parsed
        // card content visibly changes (truncation/rounding can mask a real
        // difference), so identical consecutive cards must still forward.
        val card = NavFormatter.toCard(update)
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
