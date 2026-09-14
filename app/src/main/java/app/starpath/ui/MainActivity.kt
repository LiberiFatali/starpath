package app.starpath.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import app.starpath.nav.KeepAliveService
import app.starpath.nav.NavFormatter
import app.starpath.nav.NavManeuver
import app.starpath.nav.NavNotifier
import app.starpath.nav.NavState
import app.starpath.nav.NavUpdate

/**
 * 30-second onboarding: permissions -> Zepp forwarding -> battery -> test.
 * Everything is programmatic UI (no XML) to keep v1 to one screen.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView

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

        fun addButton(label: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick(); refreshStatus() }
            })
        }

        addButton("1. Allow notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        addButton("2. Allow StarPath notifications") {
            if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        }
        addButton("3. Disable battery optimization") {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
        addButton("4. Start background listener") {
            startForegroundService(Intent(this, KeepAliveService::class.java))
        }
        addButton("5. Send test card to watch") {
            sendTestCard()
            Toast.makeText(this, "Test card sent! Check phone notification & watch", Toast.LENGTH_SHORT).show()
        }
        addButton("5b. Send test card in 5s (lock your screen)") {
            Toast.makeText(this, "Lock phone screen now! Card arrives in 5s...", Toast.LENGTH_LONG).show()
            window.decorView.postDelayed({
                sendTestCard()
            }, 5000)
        }

        val help = TextView(this).apply {
            textSize = 14f
            text = "\nOn the watch & Zepp App:\n" +
                "1. Zepp App → Profile → Active 2 → App Alerts → enable “StarPath”.\n" +
                "2. Keep notification longer on watch:\n" +
                "   • On watch: Settings → Display & Brightness → Auto Screen Off / Screen-on Duration → set to 15s–30s.\n" +
                "   • StarPath automatically re-wakes the watch at milestones (500m, 200m, 100m, 50m) and pulses every 18s approaching turns.\n" +
                "3. Zepp App → App Alerts → if “Only receive when screen is off” is ON, either turn it OFF for testing or use button 5b and lock your phone.\n" +
                "4. Ensure watch Do Not Disturb (DND) / Sleep Mode is OFF.\n\n" +
                "Then navigate in Google Maps with the phone in your pocket — " +
                "turn cards appear on the watch with large arrows. Nothing installs on the watch itself."
        }
        layout.addView(help)
        setContentView(ScrollView(this).apply { addView(layout) })
        refreshStatus()
    }

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
                )
            ),
            alert = true,
        )
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun refreshStatus() {
        val enabled = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        val me = ComponentName(this, app.starpath.nav.StarPathListener::class.java)
            .flattenToString()
        val listenerOn = me in enabled
        val pm = getSystemService(PowerManager::class.java)
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val notifsOk = NotificationManagerCompat.from(this).areNotificationsEnabled()

        status.text = "Listener: ${if (listenerOn) "ON ✓" else "OFF — tap 1"}\n" +
            "Notifs: ${if (notifsOk) "ON ✓" else "OFF — tap 2"}\n" +
            "Battery: ${if (batteryOk) "unrestricted ✓" else "optimized — tap 3"}"
    }
}
