package app.starpath.nav.model

enum class NavState { ENROUTE, REROUTING }

/**
 * One parsed navigation snapshot from the Google Maps notification.
 * All fields are display-ready raw strings; [distanceMeters] may be null
 * when the notification only carries a human-readable distance.
 */
data class NavUpdate(
    val maneuver: NavManeuver,
    val distanceText: String,
    val distanceMeters: Int?,
    val street: String,
    val tripLine: String,
    val state: NavState,
)
