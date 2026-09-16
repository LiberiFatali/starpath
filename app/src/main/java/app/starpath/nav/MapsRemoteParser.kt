package app.starpath.nav

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView

/**
 * Reads the real Google Maps navigation content from the notification's
 * custom `RemoteViews` instead of the degraded `extras` lockscreen copy.
 *
 * Background (see 3v1n0/GMapsParser): Maps posts a custom layout where
 * - `nav_title` = distance ("200 m")
 * - `nav_description` = true instruction ("Turn left onto Nguyen Hue")
 * - `nav_time` / `header_text` = trip line ("12 min · 3.2 km · 5:30 PM")
 * - lockscreen_* / title / text = fallback copies
 * - `nav_notification_icon` / `right_icon` = arrow bitmap
 *
 * StarPath previously only read `extras`, which usually contain just
 * street + ETA with no maneuver verb — so every turn parsed as UNKNOWN
 * (rendered as a straight arrow). This parser recovers the true
 * instruction; [GMapsParser] stays the keyword fallback.
 */
object MapsRemoteParser {

    private const val TAG = "StarPathRemote"

    /** Pure, unit-testable mapping of RemoteViews text fields. */
    data class RemoteFields(
        val instruction: String = "",
        val distanceLine: String = "",
        val tripLine: String = "",
    )

    /**
     * Pure mapping from collected view-id -> text into the three fields.
     * Keys are resource entry names (e.g. "nav_description").
     */
    fun pickFields(texts: Map<String, String>): RemoteFields {
        val instruction = texts["nav_description"]
            ?: texts["lockscreen_directions"]
            ?: texts["title"]
            ?: texts["lockscreen_oneliner"]
            .orEmpty()
        val distance = texts["nav_title"].orEmpty()
        val trip = texts["nav_time"]
            ?: texts["header_text"]
            ?: texts["lockscreen_eta"]
            ?: texts["text"]
            .orEmpty()
        return RemoteFields(
            instruction = instruction.trim(),
            distanceLine = distance.trim(),
            tripLine = trip.trim(),
        )
    }

    /** Pure conversion of fields into a [NavUpdate] via [GMapsParser]. */
    fun toUpdate(fields: RemoteFields): NavUpdate? {
        if (fields.instruction.isBlank() &&
            fields.distanceLine.isBlank() && fields.tripLine.isBlank()
        ) return null
        return GMapsParser.parseRemote(
            instruction = fields.instruction,
            distanceLine = fields.distanceLine,
            tripLine = fields.tripLine,
        )
    }

    /**
     * Full Android path: inflate Maps' RemoteViews with the Maps package
     * context and extract the navigation fields. Returns null when the
     * layout can't be inflated (OEM/API differences) — caller falls back
     * to extras parsing.
     */
    fun parseUpdate(sbn: StatusBarNotification, context: Context): NavUpdate? {
        return try {
            val fields = parseFields(sbn, context) ?: return null
            toUpdate(fields)
        } catch (e: Exception) {
            Log.d(TAG, "RemoteViews parse failed, falling back to extras: $e")
            null
        }
    }

    internal fun parseFields(sbn: StatusBarNotification, context: Context): RemoteFields? {
        val notification = sbn.notification
        val mapsCtx = try {
            context.createPackageContext(
                GMapsParser.MAPS_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY,
            )
        } catch (e: Exception) {
            Log.d(TAG, "Cannot create Maps context: $e")
            return null
        }

        val candidates = listOfNotNull(
            getBigContentView(notification, context),
            getContentView(notification, context),
        ).distinct()
        if (candidates.isEmpty()) return null

        for (rv in candidates) {
            try {
                val inflater = mapsCtx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)
                    as? LayoutInflater ?: continue
                val group = inflater.inflate(rv.layoutId, null) as? ViewGroup ?: continue
                rv.reapply(mapsCtx, group)
                val texts = collectTexts(group, mapsCtx)
                if (texts.isEmpty()) continue
                val fields = pickFields(texts)
                if (fields.instruction.isNotBlank()) return fields
            } catch (e: Exception) {
                Log.d(TAG, "RemoteViews candidate failed: $e")
            }
        }
        return null
    }

    private fun getBigContentView(
        notification: Notification,
        context: Context,
    ): android.widget.RemoteViews? = try {
        if (Build.VERSION.SDK_INT >= 24) {
            Notification.Builder.recoverBuilder(context, notification).createBigContentView()
        } else {
            @Suppress("DEPRECATION")
            notification.bigContentView
        }
    } catch (_: Exception) { null }

    private fun getContentView(
        notification: Notification,
        context: Context,
    ): android.widget.RemoteViews? = try {
        if (Build.VERSION.SDK_INT >= 24) {
            Notification.Builder.recoverBuilder(context, notification).createContentView()
        } else {
            @Suppress("DEPRECATION")
            notification.contentView
        }
    } catch (_: Exception) { null }

    private fun collectTexts(group: ViewGroup, mapsCtx: Context): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        collectInto(group, mapsCtx, out)
        return out
    }

    private fun collectInto(group: ViewGroup, mapsCtx: Context, out: MutableMap<String, String>) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            when (child) {
                is TextView -> {
                    val name = entryName(mapsCtx, child.id) ?: continue
                    val text = child.text?.toString().orEmpty().trim()
                    if (text.isNotBlank()) out.putIfAbsent(name, text)
                }
                is ViewGroup -> collectInto(child, mapsCtx, out)
                else -> Unit
            }
        }
    }

    private fun entryName(mapsCtx: Context, id: Int): String? {
        if (id <= 0) return null
        return try {
            mapsCtx.resources.getResourceEntryName(id)
        } catch (_: Exception) { null }
    }
}
