package app.starpath.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
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
import app.starpath.nav.runtime.DeliveryPath
import app.starpath.nav.runtime.DeliveryPaths
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
        private const val PREF_GB_DISMISSED = "gb_dismissed"
    }

    private enum class PendingStep {
        NONE,
        POST_NOTIFS,
        BATTERY,
        LISTENER,
    }

    private lateinit var status: TextView
    private lateinit var gbCard: LinearLayout
    private lateinit var gbShowButton: Button
    private lateinit var setupButton: Button
    private lateinit var asciiButton: Button
    private lateinit var lastParseView: TextView
    private var pendingStep = PendingStep.NONE

    private fun isAsciiArrows(): Boolean =
        getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE)
            .getBoolean(NavFormatter.PREF_ASCII_ARROWS, false)

    private fun isGbDismissed(): Boolean =
        getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE)
            .getBoolean(PREF_GB_DISMISSED, false)

    private fun setGbDismissed(dismissed: Boolean) {
        getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE).edit()
            .putBoolean(PREF_GB_DISMISSED, dismissed)
            .apply()
    }

    /**
     * Active delivery path: the stored one-time choice (first run), refreshed
     * on every app open (see [onResume]). The listener hot path reads the
     * same stored value, so no PackageManager probe happens per Maps post.
     */
    private fun deliveryPath() =
        DeliveryPaths.loadOrResolve(
            getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE),
        ) { DeliveryPaths.isInstalled(packageManager, it) }

    private fun deliveryLine(path: DeliveryPath): String =
        when (path) {
            DeliveryPath.ZEPP -> "Delivery: Zepp App ✓"
            DeliveryPath.GADGETBRIDGE -> "Delivery: Gadgetbridge ✓"
            DeliveryPath.BLOCKED_BOTH ->
                "Delivery: BLOCKED ✗ (keep only Zepp or only Gadgetbridge)"

            DeliveryPath.BLOCKED_NONE ->
                "Delivery: BLOCKED ✗ (install Zepp or Gadgetbridge)"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NavNotifier(this).ensureChannels()

        val dp = resources.displayMetrics.density
        val isDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "★ StarPath v${BuildConfig.VERSION_NAME}"
            textSize = 24f
        }
        layout.addView(title)

        // Highlighted Gadgetbridge setup card
        gbCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            val cardBg = GradientDrawable().apply {
                setColor(if (isDark) Color.parseColor("#332211") else Color.parseColor("#FFF3E0"))
                setStroke((2 * dp).toInt(), if (isDark) Color.parseColor("#FF9800") else Color.parseColor("#F57C00"))
                cornerRadius = 12 * dp
            }
            background = cardBg
            visibility = View.GONE
        }

        val gbTitle = TextView(this).apply {
            text = "⚠ IMPORTANT: Gadgetbridge Setup"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isDark) Color.parseColor("#FFB74D") else Color.parseColor("#E65100"))
            setPadding(0, 0, 0, (6 * dp).toInt())
        }
        gbCard.addView(gbTitle)

        val gbStep = TextView(this).apply {
            text =
                "Turn ON \"…also when screen is on\"\n(Settings → Notifications)\nCRITICAL: Gadgetbridge drops turn cards if this is off!"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isDark) Color.parseColor("#FFB74D") else Color.parseColor("#BF360C"))
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        gbCard.addView(gbStep)

        val gbActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val openGbBtn = Button(this).apply {
            text = "Open Gadgetbridge"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (4 * dp).toInt()
            }
            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(DeliveryPaths.GADGETBRIDGE_PACKAGE)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Gadgetbridge app not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val dismissGbBtn = Button(this).apply {
            text = "Dismiss"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (4 * dp).toInt()
            }
            setOnClickListener {
                setGbDismissed(true)
                refreshStatus()
                Toast.makeText(
                    this@MainActivity,
                    "Gadgetbridge notes hidden. Tap 'Show notes' anytime to view again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        gbActions.addView(openGbBtn)
        gbActions.addView(dismissGbBtn)
        gbCard.addView(gbActions)
        layout.addView(gbCard)

        gbShowButton = Button(this).apply {
            text = "▸ Show Gadgetbridge setup notes"
            visibility = View.GONE
            setOnClickListener {
                setGbDismissed(false)
                refreshStatus()
            }
        }
        layout.addView(gbShowButton)

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 8, 0, 16)
        }
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

        fun addCollapsibleSection(title: String, buildContent: (LinearLayout) -> Unit) {
            val contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(0, 0, 0, (8 * dp).toInt())
            }
            val toggleBtn = Button(this).apply {
                text = "▸ $title"
                setOnClickListener {
                    val isExpanded = contentContainer.visibility == View.VISIBLE
                    contentContainer.visibility = if (isExpanded) View.GONE else View.VISIBLE
                    text = if (isExpanded) "▸ $title" else "▾ $title"
                }
            }
            layout.addView(toggleBtn)
            buildContent(contentContainer)
            layout.addView(contentContainer)
        }

        addCollapsibleSection("Individual Settings (if needed)") { container ->
            fun addSectionButton(label: String, onClick: () -> Unit) {
                container.addView(Button(this).apply {
                    text = label
                    setOnClickListener { onClick(); refreshStatus() }
                })
            }
            addSectionButton("• Notification Access (Listener)") {
                openNotificationListenerSettings()
            }
            addSectionButton("• StarPath Notifications") {
                openAppNotificationSettings()
            }
            addSectionButton("• Battery Optimization") {
                requestBatteryExemption()
            }
        }

        addCollapsibleSection("Debug Info (for wrong-direction reports)") { container ->
            lastParseView = TextView(this).apply {
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            }
            container.addView(lastParseView)

            fun addDebugButton(label: String, onClick: () -> Unit) {
                container.addView(Button(this).apply {
                    text = label
                    setOnClickListener { onClick() }
                })
            }
            addDebugButton("↻ Refresh debug info") {
                refreshLastParse()
            }
            addDebugButton("⧉ Copy debug info") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("StarPath debug", lastParseView.text))
                Toast.makeText(this, "Debug info copied — paste it in your report", Toast.LENGTH_SHORT).show()
            }
            addDebugButton("➦ Share debug info") {
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
        }

        addCollapsibleSection("Tips & Updates") { container ->
            val tips = TextView(this).apply {
                textSize = 13f
                text = "• Set watch screen-on duration to 15s–30s, DND off, Bluetooth connected.\n" +
                        "• Keep only one companion app installed: Zepp or Gadgetbridge.\n" +
                        "• StarPath runs automatically in the background when Google Maps navigates.\n" +
                        "• If turn cards arrive only when your phone screen is off, adjust your companion app's screen settings."
                setPadding(0, (6 * dp).toInt(), 0, (8 * dp).toInt())
            }
            container.addView(tips)

            container.addView(Button(this).apply {
                text = "Check for Updates (F-Droid / GitHub)"
                setOnClickListener {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LiberiFatali/starpath/releases"))
                    )
                }
            })
        }

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
        val card = NavFormatter.toCard(
            NavUpdate(
                maneuver = NavManeuver.TURN_LEFT,
                street = "Nguyen Hue",
                tripLine = "12 min · 3.2 km left",
                state = NavState.ENROUTE,
            ),
            isAsciiArrows(),
        )
        // Test cards obey the same single-path gate as live navigation, and
        // use the same mirrored phone card on both paths.
        when (val path = deliveryPath()) {
            DeliveryPath.ZEPP, DeliveryPath.GADGETBRIDGE -> {
                NavNotifier(this).post(card, alert = true)
                Toast.makeText(this, "Test card sent! Check phone notification & watch", Toast.LENGTH_SHORT).show()
            }

            else -> {
                Toast.makeText(this, deliveryLine(path), Toast.LENGTH_LONG).show()
            }
        }
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
        // Re-resolve on every open so install/uninstall changes take effect;
        // opening the app is already part of every setup/debug flow.
        DeliveryPaths.refreshStored(
            getSharedPreferences(NavFormatter.PREFS_FILE, MODE_PRIVATE),
        ) { DeliveryPaths.isInstalled(packageManager, it) }
        refreshStatus()
        when (pendingStep) {
            PendingStep.BATTERY -> {
                pendingStep = PendingStep.NONE
                if (isBatteryOptimizationDisabled()) {
                    startOnboarding()
                } else {
                    Toast.makeText(
                        this,
                        "Battery optimization was not disabled. Tap Grant Permissions to retry.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            PendingStep.LISTENER -> {
                pendingStep = PendingStep.NONE
                if (isNotificationListenerEnabled()) {
                    Toast.makeText(this, "StarPath setup is complete!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Notification access was not enabled. Tap Grant Permissions to retry.",
                        Toast.LENGTH_SHORT
                    ).show()
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
        val path = deliveryPath()
        val allOk = listenerOn && notifsOk && batteryOk

        val gadgetbridge = path == DeliveryPath.GADGETBRIDGE
        if (gadgetbridge) {
            val dismissed = isGbDismissed()
            gbCard.visibility = if (dismissed) View.GONE else View.VISIBLE
            gbShowButton.visibility = if (dismissed) View.VISIBLE else View.GONE
        } else {
            gbCard.visibility = View.GONE
            gbShowButton.visibility = View.GONE
        }

        status.text = "Notification Access: ${if (listenerOn) "ON ✓" else "OFF ✗"}\n" +
                "StarPath Notifications: ${if (notifsOk) "ON ✓" else "OFF ✗"}\n" +
                "Battery Optimization: ${if (batteryOk) "unrestricted ✓" else "optimized ✗"}\n" +
                "${deliveryLine(path)}\n" +
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
