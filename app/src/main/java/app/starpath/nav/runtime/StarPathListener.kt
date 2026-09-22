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
 * delivers it as a glanceable turn card through exactly one companion app:
 * the Zepp App or Gadgetbridge (both mirror our phone notification over
 * BLE). Both installed — or neither — blocks all delivery until the user
 * keeps exactly one (see [DeliveryPaths]).
 */
class StarPathListener : NotificationListenerService() {

    private lateinit var notifier: NavNotifier
    private val alertManager = NavAlertManager()
    private var mapsActive = false
    private var blockedNotified = false
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
        // Single-path gate over the stored one-time choice (chosen at first
        // run, refreshed on every MainActivity open — no per-post probing).
        // Blocked states start nothing: no parsing, no KeepAlive, one notice
        // per Maps session explaining why.
        val path = DeliveryPaths.loadOrResolve(
            getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE),
        ) { DeliveryPaths.isInstalled(packageManager, it) }
        if (!path.isDeliverable) {
            LastParse.storeSkipped(blockedReason(path))
            if (!blockedNotified) {
                notifier.postBlockedNotice(path)
                blockedNotified = true
            }
            return
        }
        if (blockedNotified) {
            notifier.cancelBlockedNotice()
            blockedNotified = false
        }
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
        // UNKNOWN prompt with no icon verdict. Diagnostics are already stored.
        // Rerouting never latches: any live ENROUTE instruction clears a
        // stale REROUTING card (state-transition bypass inside shouldForward).
        if (!NavGate.shouldForward(update, outcome.appliedIcon, lastPosted)) return

        // Alert first: NavAlertManager tracks instruction changes and the
        // stale-instruction reminder (30s for DEST arrival, 5min otherwise).
        // Its NONE verdicts record no state, so
        // evaluating speculatively here is safe.
        val alertDecision = alertManager.evaluate(update)

        // Strict dedup: Maps re-posts frequently with no new direction/place/
        // state. Skip re-posts whose canonical direction + normalized street
        // + state match the last card — unless the alert manager flagged a
        // maneuver change, rerouting, or stale reminder worth buzzing for.
        if (NavDedup.isRedundant(update, lastPosted) && !alertDecision.shouldAlert) return

        if (!mapsActive) {
            mapsActive = true
            KeepAliveService.start(this)
        }

        // Strict dedup compares full update.street (not the truncated card
        // text), so truncation can't mask a real street change. Display
        // collapses to left/right/straight/?; ASCII mode is a
        // MainActivity-only toggle for watches missing arrow glyphs.
        val useAscii = getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE)
            .getBoolean(NavFormatter.PREF_ASCII_ARROWS, false)
        val card = NavFormatter.toCard(update, useAscii)
        // Single delivery model for both paths: re-post the phone card and
        // let the companion app (Zepp App Alerts or Gadgetbridge
        // notification mirroring) forward it over BLE. Cancelling the same
        // card on navigation end (see onNotificationRemoved) therefore
        // clears the watch on both paths — direct broadcast intents to the
        // companion app could never be retracted, so only the mirrored
        // phone card is used.
        notifier.post(card, alert = alertDecision.shouldAlert)
        lastPosted = update
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != GMapsParser.MAPS_PACKAGE) return
        if (!mapsActive && !blockedNotified) return
        // Maps cleared its navigation session -> clear our card too, and
        // arm the blocked notice again for the next session if still blocked.
        val stillThere = try {
            activeNotifications?.any {
                it != null &&
                    it.packageName == GMapsParser.MAPS_PACKAGE &&
                    it.notification.flags.hasFlag(Notification.FLAG_ONGOING_EVENT)
            } ?: false
        } catch (_: Exception) { true }
        if (!stillThere) {
            if (mapsActive) {
                mapsActive = false
                alertManager.reset()
                lastPosted = null
                notifier.cancel()
                KeepAliveService.stop(this)
            }
            blockedNotified = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        KeepAliveService.stop(this)
    }

    private fun Int.hasFlag(flag: Int): Boolean = (this and flag) == flag

    private fun blockedReason(path: DeliveryPath): String =
        when (path) {
            DeliveryPath.BLOCKED_BOTH ->
                "blocked: both Zepp and Gadgetbridge installed — keep only one"
            else ->
                "blocked: neither Zepp nor Gadgetbridge installed"
        }
}
