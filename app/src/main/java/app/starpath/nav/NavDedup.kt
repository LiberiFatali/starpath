package app.starpath.nav

/**
 * Strict dedup for Maps countdown spam: Maps re-posts every 10–20 m while
 * only the distance shrinks. The parsed [NavUpdate] already splits
 * direction ([NavManeuver]) from place ([NavUpdate.street]), so the dedup
 * key is (canonical direction, normalized street, state). Distance, raw
 * title and trip/ETA lines are deliberately excluded — otherwise every
 * countdown tick looks "new" and the watch buzzes nonstop.
 *
 * Pure Kotlin so it stays JVM-unit-testable.
 */
object NavDedup {

    data class Key(
        val maneuver: NavManeuver,
        val street: String,
        val state: NavState,
    )

    /** Collapse whitespace, normalize ` / ` spacing, lowercase (keeps diacritics). */
    fun normalizeStreet(s: String): String =
        s.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*/\\s*"), " / ")
            .lowercase()

    fun keyOf(update: NavUpdate): Key =
        Key(update.maneuver.canonical(), normalizeStreet(update.street), update.state)

    /**
     * True when [update] carries no new direction/place/state vs [last] and
     * the re-post should be skipped entirely. Null [last] (first post after
     * start/reset) is never redundant.
     */
    fun isRedundant(update: NavUpdate, last: NavUpdate?): Boolean {
        if (last == null) return false
        return keyOf(update) == keyOf(last)
    }
}
