package app.starpath.nav.runtime

import android.content.Context
import android.content.Intent

/**
 * Extra delivery path (issue #1): forwards the exact same
 * [NavFormatter.Card] to Gadgetbridge via its PebbleKit intake
 * (`com.getpebble.action.SEND_NOTIFICATION`, `PEBBLE_ALERT`), explicitly
 * addressed to the Gadgetbridge package. Proven on Amazfit Active 2 with
 * Pebble Messages set to Always (issue #2, Sep 2026).
 *
 * Fire-and-forget: PebbleKit alerts need no cancel. Failures (not installed,
 * receiver gone) report false and must never crash the navigation pipeline.
 */
class GadgetbridgeSender(private val context: Context) {

    companion object {
        const val ACTION_SEND_NOTIFICATION = "com.getpebble.action.SEND_NOTIFICATION"
        const val MESSAGE_TYPE_PEBBLE_ALERT = "PEBBLE_ALERT"
        const val EXTRA_MESSAGE_TYPE = "messageType"
        const val EXTRA_NOTIFICATION_DATA = "notificationData"

        /** Title/body payload; pure JSON so it stays JVM-tested (no org.json). */
        fun notificationDataJson(title: String, body: String): String =
            """[{"title":"${escapeJson(title)}","body":"${escapeJson(body)}"}]"""

        fun bodyOf(card: NavFormatter.Card): String =
            listOf(card.text, card.sub).filter { it.isNotBlank() }.joinToString("\n")

        fun buildIntent(card: NavFormatter.Card): Intent =
            Intent(ACTION_SEND_NOTIFICATION)
                .putExtra(EXTRA_MESSAGE_TYPE, MESSAGE_TYPE_PEBBLE_ALERT)
                .putExtra(EXTRA_NOTIFICATION_DATA, notificationDataJson(card.title, bodyOf(card)))
                .setPackage(DeliveryPaths.GADGETBRIDGE_PACKAGE)

        private fun escapeJson(s: String): String = buildString {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
        }
    }

    /** @return false when nothing was sent (never throws). */
    fun send(card: NavFormatter.Card): Boolean =
        try {
            context.sendBroadcast(buildIntent(card))
            true
        } catch (_: Exception) {
            false
        }
}
