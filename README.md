# StarPath - Google Maps Turns on Amazfit Active 2 ★

StarPath intercepts Google Maps turn-by-turn navigation notifications, reformats them into high-contrast glanceable cards (`◀◀ 200 m / Nguyen Hue`), and re-posts them so the **Zepp app** mirrors them to your Amazfit Active 2 over Bluetooth. Phone stays in your pocket.

---

## 🚀 How to Use

1. **Install the App**:
   Download the latest signed `starpath-v*.apk` from **GitHub Releases** and install it on your Android phone.
2. **Grant Permissions (1-Tap Setup)**:
   Open StarPath and tap **Grant Permissions (1-Tap Setup)** to enable notification access, battery optimization exemption, and notifications.
3. **Enable in Zepp App**:
   Open **Zepp App** → **Device** (bottom bar) → select **Active 2** → **Notifications and Reminders** → **App Alerts** → toggle **StarPath** ON (tap **Manage Apps** at the bottom to check StarPath if not listed).
   *(Tip: Tap "Send test card to watch" in StarPath first if StarPath does not appear in Zepp's app list yet).*
4. **Ride with Google Maps**:
   Start navigation in Google Maps. StarPath starts automatically in the background, delivers live turn updates and vibration pulses before intersections, and shuts itself down when you arrive.

> [!TIP]
> **Screen-off Alert Tip**: In Zepp App → *Notifications and Reminders* → *App Alerts*, if **"Only receive when the screen is off"** is enabled, Zepp will not send notifications to the watch while your phone screen is turned on. Either turn that setting OFF for testing, or tap **"Send test card in 5s"** in StarPath and immediately lock your phone screen.

---

## 🛠️ How to Build

### Prerequisites
- JDK 17+
- Android SDK with platform `android-36` (`export ANDROID_HOME=~/Android/Sdk` or configure `local.properties`)

### Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build signed release APK & Play Store Bundle
./gradlew :app:assembleRelease :app:bundleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest
```

Output artifacts:
- Release APK: `app/build/outputs/apk/versioned/starpath-v*.apk`
- Release Bundle: `app/build/outputs/bundle/versioned/starpath-v*.aab`

---

## 📚 Documentation

- **[IMPLEMENTATION.md](IMPLEMENTATION.md)** — Architectural diagrams, component roles, stay-awake pulse algorithms, and developer debugging steps.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Development setup, notification capture guide, and PR guidelines.
- **[PRIVACY_POLICY.md](PRIVACY_POLICY.md)** — Privacy policy and data handling (100% on-device, zero telemetry).

---

## ⚠️ Safety

Glance only — never interact with your watch while riding or driving.
