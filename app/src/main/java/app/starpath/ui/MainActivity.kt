package app.starpath.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import app.starpath.BuildConfig
import app.starpath.nav.model.NavManeuver
import app.starpath.nav.model.NavState
import app.starpath.nav.model.NavUpdate
import app.starpath.nav.parse.LastParse
import app.starpath.nav.runtime.KeepAliveService
import app.starpath.nav.runtime.NavFormatter
import app.starpath.nav.runtime.NavNotifier
import app.starpath.nav.runtime.StarPathListener

/**
 * Onboarding and controls for StarPath:
 * - 1-tap chained permissions flow
 * - Automatic background service lifecycle (starts on Maps navigation, stops on exit)
 * - Test cards & clean exit option
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_POST_NOTIFS = 101
    }

    private enum class PendingStep {
        NONE,
        POST_NOTIFS,
        BATTERY,
        LISTENER,
    }

    private lateinit var status: TextView
    private lateinit var setupButton: Button
    private lateinit var asciiButton: Button
    private lateinit var lastParseView: TextView
    private var pendingStep = PendingStep.NONE

    private fun isAsciiArrows(): Boolean =
        getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE)
            .getBoolean(NavFormatter.PREF_ASCII_ARROWS, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NavNotifier(this).ensureChannels()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "★ StarPath v${BuildConfig.VERSION_NAME}"
            textSize = 24f
        }
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 8, 0, 16)
        }
        layout.addView(title)
        layout.addView(status)

        setupButton = Button(this).apply {
            setOnClickListener { startOnboarding() }
        }
        layout.addView(setupButton)

        fun addButton(label: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick(); refreshStatus() }
            })
        }

        addButton("Send test card to watch") {
            sendTestCard()
            Toast.makeText(this, "Test card sent! Check phone notification & watch", Toast.LENGTH_SHORT).show()
        }

        addButton("Send test card in 5s (lock your screen)") {
            Toast.makeText(this, "Lock phone screen now! Card arrives in 5s...", Toast.LENGTH_LONG).show()
            window.decorView.postDelayed({
                sendTestCard()
            }, 5000)
        }

        asciiButton = Button(this).apply {
            setOnClickListener {
                val next = !isAsciiArrows()
                getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE).edit()
                    .putBoolean(NavFormatter.PREF_ASCII_ARROWS, next)
                    .apply()
                refreshAsciiButton()
                Toast.makeText(
                    this@MainActivity,
                    if (next) "ASCII arrows ON (<- -> ^ ?)" else "Arrow glyphs ON (◀◀ ▶▶ ▲▲ ?)",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        layout.addView(asciiButton)
        refreshAsciiButton()

        addButton("Stop & Exit StarPath") {
            stopAndExit()
        }

        val debugHeader = TextView(this).apply {
            textSize = 13f
            text = "\nLast Maps notification (for wrong-direction reports):"
            setPadding(0, 16, 0, 4)
        }
        layout.addView(debugHeader)
        lastParseView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 4)
        }
        layout.addView(lastParseView)

        addButton("↻ Refresh debug info") {
            refreshLastParse()
        }
        addButton("⧉ Copy debug info") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("StarPath debug", lastParseView.text))
            Toast.makeText(this, "Debug info copied — paste it in your report", Toast.LENGTH_SHORT).show()
        }
        addButton("➦ Share debug info") {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "StarPath wrong direction report")
                        putExtra(Intent.EXTRA_TEXT, lastParseView.text.toString())
                    },
                    "Share debug info",
                )
            )
        }

        val individualSectionHeader = TextView(this).apply {
            textSize = 13f
            text = "\nIndividual Settings (if needed):"
            setPadding(0, 16, 0, 4)
        }
        layout.addView(individualSectionHeader)

        addButton("• Notification Access (Listener)") {
            openNotificationListenerSettings()
        }
        addButton("• StarPath Notifications") {
            openAppNotificationSettings()
        }
        addButton("• Battery Optimization") {
            requestBatteryExemption()
        }

        val help = TextView(this).apply {
            textSize = 14f
            text = "\nOn the watch & Zepp App (e.g. Amazfit Active 2):\n" +
                "1. In the Zepp App, open your paired watch and enable notification/alert mirroring, then select “StarPath” (send a test card first if StarPath isn't listed yet).\n" +
                "2. Keep notification longer on watch:\n" +
                "   • On watch: display/screen settings → screen-on duration → set to 15s–30s.\n" +
                "   • StarPath automatically re-wakes the watch at milestones (500m, 200m, 100m, 50m) and pulses every 18s approaching turns.\n" +
                "3. If your companion app has a “receive only when phone screen is off” option and it is ON, either turn it OFF for testing or use the 5s delayed button and lock your phone.\n" +
                "4. Ensure watch Do Not Disturb (DND) / Sleep Mode is OFF and the watch stays connected over Bluetooth.\n\n" +
                "Then navigate in Google Maps with phone in your pocket — " +
                "background service starts automatically and turn cards appear live on the watch. " +
                "When navigation ends, the background service stops itself."
        }
        layout.addView(help)
        setContentView(ScrollView(this).apply { addView(layout) })
        refreshStatus()
    }

    private fun startOnboarding() {
        val notifsOk = isNotificationsEnabled()
        val batteryOk = isBatteryOptimizationDisabled()
        val listenerOk = isNotificationListenerEnabled()

        when {
            !notifsOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                pendingStep = PendingStep.POST_NOTIFS
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFS)
            }
            !batteryOk -> {
                pendingStep = PendingStep.BATTERY
                requestBatteryExemption()
            }
            !listenerOk -> {
                pendingStep = PendingStep.LISTENER
                openNotificationListenerSettings()
            }
            else -> {
                pendingStep = PendingStep.NONE
                Toast.makeText(this, "All permissions granted! StarPath is ready.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNotificationListenerSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(this@MainActivity, StarPathListener::class.java).flattenToString(),
                )
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun openAppNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIFS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm?.isIgnoringBatteryOptimizations(packageName) != true) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        val me = ComponentName(this, StarPathListener::class.java).flattenToString()
        return me in enabled
    }

    private fun isNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(packageName) ?: false
    }

    private fun allPermissionsGranted(): Boolean =
        isNotificationListenerEnabled() && isNotificationsEnabled() && isBatteryOptimizationDisabled()

    private fun sendTestCard() {
        NavNotifier(this).post(
            NavFormatter.toCard(
                NavUpdate(
                    maneuver = NavManeuver.TURN_LEFT,
                    distanceText = "200 m",
                    distanceMeters = 200,
                    street = "Nguyen Hue",
                    tripLine = "12 min · 3.2 km left",
                    state = NavState.ENROUTE,
                ),
                isAsciiArrows(),
            ),
            alert = true,
        )
    }

    private fun refreshAsciiButton() {
        asciiButton.text =
            if (isAsciiArrows()) "ASCII arrows: ON (<- -> ^ ?)" else "ASCII arrows: OFF (◀◀ ▶▶ ▲▲ ?)"
    }

    private fun stopAndExit() {
        KeepAliveService.stop(this)
        NavNotifier(this).cancel()
        Toast.makeText(this, "StarPath stopped", Toast.LENGTH_SHORT).show()
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        when (pendingStep) {
            PendingStep.BATTERY -> {
                pendingStep = PendingStep.NONE
                if (isBatteryOptimizationDisabled()) {
                    startOnboarding()
                } else {
                    Toast.makeText(this, "Battery optimization was not disabled. Tap Grant Permissions to retry.", Toast.LENGTH_SHORT).show()
                }
            }
            PendingStep.LISTENER -> {
                pendingStep = PendingStep.NONE
                if (isNotificationListenerEnabled()) {
                    Toast.makeText(this, "StarPath setup is complete!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Notification access was not enabled. Tap Grant Permissions to retry.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {}
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
        if (requestCode == REQ_POST_NOTIFS && pendingStep == PendingStep.POST_NOTIFS) {
            pendingStep = PendingStep.NONE
            if (isNotificationsEnabled()) {
                startOnboarding()
            } else {
                Toast.makeText(this, "Notification permission is needed to show turn cards", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshStatus() {
        val listenerOn = isNotificationListenerEnabled()
        val notifsOk = isNotificationsEnabled()
        val batteryOk = isBatteryOptimizationDisabled()
        val allOk = listenerOn && notifsOk && batteryOk

        status.text = "Notification Access: ${if (listenerOn) "ON ✓" else "OFF ✗"}\n" +
            "StarPath Notifications: ${if (notifsOk) "ON ✓" else "OFF ✗"}\n" +
            "Battery Optimization: ${if (batteryOk) "unrestricted ✓" else "optimized ✗"}\n" +
            "Background Listener: Automatic (starts when navigating in Maps) ✓"

        if (allOk) {
            setupButton.text = "✓ StarPath Ready (All Permissions Granted)"
            setupButton.isEnabled = false
        } else {
            setupButton.text = "Grant Permissions (1-Tap Setup)"
            setupButton.isEnabled = true
        }
        if (::asciiButton.isInitialized) refreshAsciiButton()
        refreshLastParse()
    }

    private fun refreshLastParse() {
        if (::lastParseView.isInitialized) {
            lastParseView.text = LastParse.current
        }
    }
}
