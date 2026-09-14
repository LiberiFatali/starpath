# ★ StarPath — Google Maps Turns on Amazfit Active 2

StarPath intercepts Google Maps turn-by-turn navigation notifications, reformats them into high-contrast glanceable cards (`◀◀ 200 m / Nguyen Hue`), and re-posts them so the **Zepp app** mirrors them to your Amazfit Active 2 over Bluetooth. Phone stays in your pocket.

---

## 🚀 How to Use

1. **Install the App**:
   Download the latest signed `starpath-v*.apk` from **[GitHub Releases](https://github.com/LiberiFatali/starpath/releases)** and install it on your Android phone.
2. **Grant Permissions (1-Tap Setup)**:
   Open StarPath and tap **Grant Permissions (1-Tap Setup)** to enable notification access, battery optimization exemption, and notifications.
3. **Enable in Zepp App**:
   Open **Zepp App** → **Profile** → **Active 2** → **App Alerts / Notifications** → **Manage Apps** → toggle **StarPath** ON.
4. **Ride with Google Maps**:
   Start navigation in Google Maps. StarPath starts automatically in the background, delivers live turn updates and vibration pulses before intersections, and shuts itself down when you arrive.

> [!TIP]
> **Troubleshooting tip**: In Zepp App → *App Alerts*, if **"Only receive when the screen is off"** is enabled, Zepp drops notifications forwarded while your phone screen is active. Either toggle it off or use the **"Send test card in 5s"** button in StarPath and lock your phone screen to test.

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

## 🤝 How to Contribute

Contributions, bug reports, and new language parsers are welcome!

1. **Fork and Clone** the repository.
2. **Create a Feature Branch**:
   ```bash
   git checkout -b feat/support-new-language
   ```
3. **Add or Fix Parsing Rules**:
   - Add new maneuver keywords or language patterns in `app/src/main/java/app/starpath/nav/GMapsParser.kt`.
   - Add unit test cases with raw Google Maps notification text in `app/src/test/java/app/starpath/nav/GMapsParserTest.kt`.
   - See **[IMPLEMENTATION.md](IMPLEMENTATION.md)** for architecture details and notification dumping instructions.
4. **Verify All Tests Pass**:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```
5. **Open a Pull Request**: Provide a description of the changes and sample notification text dumps if adding new syntax.

---

## 📚 Documentation & Policies

- **[IMPLEMENTATION.md](IMPLEMENTATION.md)** — Architectural diagrams, component roles, stay-awake pulse algorithms, and developer debugging steps.
- **[PRIVACY_POLICY.md](PRIVACY_POLICY.md)** — Privacy policy and data handling (100% on-device, zero data collection, zero network calls).

---

## ⚠️ Safety

Glance only — never interact with your watch while riding or driving.
