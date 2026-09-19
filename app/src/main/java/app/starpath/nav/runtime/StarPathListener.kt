package app.starpath.nav.runtime

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.starpath.nav.model.NavUpdate
import app.starpath.nav.parse.GMapsParser
import app.starpath.nav.parse.LastParse
import app.starpath.nav.parse.MapsRemoteParser

/**
 * Intercepts the Google Maps navigation notification, parses it, and
 * re-posts it as StarPath's glanceable card (which Zepp forwards to the watch).
 */
class StarPathListener : NotificationListenerService() {

    private lateinit var notifier: NavNotifier
    private val alertManager = NavAlertManager()
    private var mapsActive = false
    internal var lastPosted: NavUpdate? = null

    override fun onCreate() {
        super.onCreate()
        notifier = NavNotifier(this).also { it.ensureChannels() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != GMapsParser.MAPS_PACKAGE) return
        // Navigation phase only: Maps posts active navigation as ongoing.
        // Transient Maps pushes (crowdsource "Has it closed?" prompts, shares)
        // are never ongoing — drop them before parsing so nothing reaches
        // the phone card or the watch outside navigation.
        if (!sbn.notification.flags.hasFlag(Notification.FLAG_ONGOING_EVENT)) return
        // Lightweight pipeline: largeIcon arrow pixels first, extras text
        // as fallback. Decision order: confident icon > text verb > UNKNOWN
        // (never fake-straight; bare "toward X" is UNKNOWN).
        // The watch only shows text glyphs, so the icon verdict is rendered
        // as a glyph too — see IconClassifier KDoc.
        val outcome = MapsRemoteParser.parseOutcome(sbn, this)
        LastParse.store(outcome)
        val update = outcome.update ?: return

        // Navigation-phase validity gate (defense in depth behind the ongoing
        // flag): drop non-navigation content that still parses, e.g. an
        // UNKNOWN prompt with no distance. Diagnostics are already stored.
        // Rerouting never latches: any live ENROUTE instruction clears a
        // stale REROUTING card (state-transition bypass inside shouldForward).
        if (!NavGate.shouldForward(update, outcome.appliedIcon, lastPosted)) return

        // Strict dedup: Maps re-posts every 10–20 m with only the distance
        // shrinking. The parsed update already splits direction (maneuver)
        // from place (street), so skip re-posts whose canonical direction +
        // normalized street + state match the last card — even milestones.
        // Distance title intentionally freezes between turns to stop watch spam.
        if (NavDedup.isRedundant(update, lastPosted)) return

        if (!mapsActive) {
            mapsActive = true
            KeepAliveService.start(this)
        }

        val alertDecision = alertManager.evaluate(update)

        // Strict dedup compares full update.street (not the truncated card
        // text), so truncation can't mask a real street change. Display
        // collapses to left/right/straight/?; ASCII mode is a
        // MainActivity-only toggle for watches missing arrow glyphs.
        val useAscii = getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE)
            .getBoolean(NavFormatter.PREF_ASCII_ARROWS, false)
        val card = NavFormatter.toCard(update, useAscii)
        notifier.post(card, alert = alertDecision.shouldAlert)
        lastPosted = update
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
            lastPosted = null
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
