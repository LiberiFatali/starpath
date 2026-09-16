package app.starpath.nav

/**
 * Latest Maps parse outcome, kept in memory for the on-device debug screen
 * in `MainActivity` ("Last notification"). Phone-only debugging (no adb)
 * depends on this: the user copies/shares the dump when a direction is wrong.
 */
object LastParse {
    @Volatile
    var current: String = "No Maps notification seen yet."
        private set

    @Volatile
    var timestampMs: Long = 0L
        private set

    fun store(outcome: MapsRemoteParser.Outcome) {
        timestampMs = System.currentTimeMillis()
        current = buildString {
            appendLine("time=${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestampMs))}")
            appendLine("note=${outcome.note}")
            val u = outcome.update
            if (u == null) {
                appendLine("update=null")
            } else {
                appendLine("maneuver=${u.maneuver} state=${u.state}")
                appendLine("distance='${u.distanceText}' meters=${u.distanceMeters}")
                appendLine("street='${u.street}'")
                appendLine("trip='${u.tripLine}'")
            }
            appendLine(
                "icon=${outcome.iconManeuver} " +
                    "score=${"%.2f".format(outcome.iconScore)} " +
                    "applied=${outcome.appliedIcon}",
            )
            if (outcome.headScores.isNotBlank()) appendLine("heads=${outcome.headScores}")
            if (outcome.maskArt.isNotBlank()) {
                appendLine("mask:")
                outcome.maskArt.lines().forEach { appendLine("  $it") }
            }
            appendLine("fields:")
            if (outcome.texts.isEmpty()) {
                appendLine("  (none)")
            } else {
                for ((k, v) in outcome.texts) {
                    val trimmed = if (v.length <= 80) v else v.take(77) + "..."
                    appendLine("  $k='$trimmed'")
                }
            }
        }
    }

    fun storeSkipped(reason: String) {
        timestampMs = System.currentTimeMillis()
        current = "skipped: $reason"
    }
}
