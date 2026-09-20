package app.starpath.nav.model

enum class NavState { ENROUTE, REROUTING }

/**
 * One parsed navigation snapshot from the Google Maps notification.
 *
 * Next-turn distance is deliberately absent: it cannot be extracted reliably
 * from the Maps notification, so nothing may depend on it. The trip line
 * (remaining trip distance + duration) is display-only via [NavFormatter].
 */
data class NavUpdate(
    val maneuver: NavManeuver,
    val street: String,
    val tripLine: String,
    val state: NavState,
)
