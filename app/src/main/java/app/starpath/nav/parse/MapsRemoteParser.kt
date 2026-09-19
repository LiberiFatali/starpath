package app.starpath.nav.parse

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.service.notification.StatusBarNotification
import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate

/**
 * Lightweight Maps notification parser: `largeIcon` arrow pixels first,
 * `extras` text as fallback.
 *
 * Deliberately no RemoteViews inflation (no `createPackageContext`,
 * `LayoutInflater`, view-id maps): it was heavy, fragile across Maps
 * releases, and unnecessary once the icon classifier handles icon-only
 * instructions ("40 m" + street → `◀◀`/`▶▶` via moments).
 * [IconClassifier] decides direction when confident; [GMapsParser] covers
 * the rest (street/trip, rerouting, text-only verbs, icon-missing frames).
 */
object MapsRemoteParser {

    /** Where the winning update came from. */
    enum class Source { EXTRAS, NONE }

    /**
     * Full pipeline result for one Maps post, including everything the
     * on-device debug screen needs. [iconManeuver] is the classifier verdict
     * on the Maps arrow pixels (null when no icon was captured);
     * [appliedIcon] is true when the icon decided the final maneuver.
     */
    data class Outcome(
        val update: NavUpdate?,
        val source: Source,
        val texts: Map<String, String>,
        val iconManeuver: NavManeuver?,
        val iconScore: Double,
        val appliedIcon: Boolean,
        val note: String,
        /** 16 `#`/`.` mask rows used by the icon classifier ("" when skipped). */
        val maskArt: String = "",
        /** Confidence per direction, e.g. "L=0.86 R=0.00 U=0.00". */
        val headScores: String = "",
    )

    /**
     * End-to-end parse: largeIcon pixels first, extras text as fallback.
     * Never throws; a null [Outcome.update] with a note means "nothing
     * usable" and the caller should skip the post.
     */
    fun parseOutcome(sbn: StatusBarNotification, context: Context): Outcome {
        val extras = sbn.notification.extras
        val extrasSummary = linkedMapOf(
            "title" to extras.getString(Notification.EXTRA_TITLE).orEmpty(),
            "text" to extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            "bigText" to extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            "textLines" to (extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString(" | ") { it.toString() }.orEmpty()),
            "subText" to extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
        )

        var update = GMapsParser.parse(
            title = extrasSummary["title"],
            text = extrasSummary["text"],
            bigText = extrasSummary["bigText"],
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map { it.toString() }.orEmpty(),
            subText = extrasSummary["subText"],
        )
        val source = if (update != null) Source.EXTRAS else Source.NONE

        if (update == null) {
            return Outcome(
                update = null,
                source = Source.NONE,
                texts = extrasSummary.filterValues { it.isNotBlank() },
                iconManeuver = null,
                iconScore = 0.0,
                appliedIcon = false,
                note = "no parsable content",
            )
        }

        // Icon-first: the arrow is the direction signal (language-free,
        // immune to ambiguous phrasing like bare "toward X" — field case
        // turn_right_with_toward_text). Text is the fallback for icon-missing
        // or low-confidence frames; moments (topMeanX − bottomMeanX) ignore
        // shaft thickness/style.
        var iconManeuver: NavManeuver? = null
        var iconScore = 0.0
        var appliedIcon = false
        var maskArt = ""
        var headScores = ""
        var suppressNote = ""
        if (update.state == NavState.ENROUTE) {
            val pixels = parseIconPixels(sbn, context)
            if (pixels != null) {
                val detail = IconClassifier.classifyDetailed(pixels)
                iconManeuver = detail.maneuver
                iconScore = detail.score
                maskArt = detail.maskArt
                headScores = detail.scores.entries.joinToString(" ") { (m, s) ->
                    val tag = when (m) {
                        NavManeuver.TURN_LEFT -> "L"
                        NavManeuver.TURN_RIGHT -> "R"
                        NavManeuver.STRAIGHT -> "S"
                        NavManeuver.DESTINATION -> "D"
                        else -> "U"
                    }
                    "$tag=%.2f".format(s)
                }
                if (detail.maneuver != NavManeuver.UNKNOWN) {
                    // A destination pin only appears at arrival: far from it
                    // the icon is a lookalike (e.g. roundabout loop), so stay
                    // on the text verdict instead of posting a false DEST.
                    if (detail.maneuver == NavManeuver.DESTINATION &&
                        !GMapsParser.isPlausibleDestination(update.tripLine)
                    ) {
                        suppressNote = " icon DEST suppressed (trip far)"
                    } else {
                        if (update.maneuver != detail.maneuver) {
                            suppressNote = if (update.maneuver == NavManeuver.UNKNOWN) {
                                " icon→${detail.maneuver}"
                            } else {
                                " icon→${detail.maneuver} over text ${update.maneuver}"
                            }
                        }
                        update = update.copy(maneuver = detail.maneuver)
                        appliedIcon = true
                    }
                }
            }
        }

        return Outcome(
            update = update,
            source = source,
            texts = extrasSummary.filterValues { it.isNotBlank() },
            iconManeuver = iconManeuver,
            iconScore = iconScore,
            appliedIcon = appliedIcon,
            maskArt = maskArt,
            headScores = headScores,
            note = buildString {
                append("source=$source")
                append(suppressNote)
            },
        )
    }

    /**
     * Captures the Maps direction arrow pixels from the notification's
     * large icon. Returns null when no bitmap is available.
     */
    fun parseIconPixels(
        sbn: StatusBarNotification,
        context: Context,
    ): IconClassifier.IconPixels? {
        return try {
            if (Build.VERSION.SDK_INT < 23) return null
            val icon = sbn.notification.getLargeIcon() ?: return null
            val drawable = icon.loadDrawable(context) as? BitmapDrawable ?: return null
            toPixels(drawable.bitmap)
        } catch (_: Exception) {
            null
        }
    }

    private fun toPixels(bitmap: Bitmap): IconClassifier.IconPixels? {
        return try {
            val safe = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            } ?: return null
            val pixels = IntArray(safe.width * safe.height)
            safe.getPixels(pixels, 0, safe.width, 0, 0, safe.width, safe.height)
            IconClassifier.IconPixels(safe.width, safe.height, pixels)
        } catch (_: Exception) {
            null
        }
    }
}
