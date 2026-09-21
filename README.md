# ★ StarPath - Google Maps turn-by-turn for Amazfit watches and Zepp/Gadgetbridge devices

**Why:** Most Amazfit watches and Zepp-paired devices have no Google Maps turn-by-turn support. StarPath puts it on your wrist, so your phone stays in your pocket (tested on Amazfit Active 2 — see Compatibility).

<img src="docs/assets/arrow/3_maps_starpath_turn_right.jpg" alt="Glanceable Card" width="360" />

**How:** StarPath reads Google Maps navigation notifications and re-posts them as high-contrast cards (`▶▶ / Nguyen Hue / DEST 450 m · 6 min`) that the Zepp/Gadgetbridge app mirrors to your watch over Bluetooth. See `docs/COMPATIBILITY.md` for the display model (mark + street + remaining-trip line; no next-turn distance).

---

## 🚀 How to Use

1. **Install the App**:
   Download the latest signed `starpath-v*.apk` from **GitHub Releases** and install it on your Android phone.
2. **Grant Permissions (1-Tap Setup)**:
   Open StarPath and tap **Grant Permissions (1-Tap Setup)** to enable notification access, battery optimization exemption, and notifications.
3. **Enable in Zepp App or Gadgetbridge (keep only one)**:
    In the Zepp App, open your paired watch and enable notification/alert mirroring, then select **StarPath** (tap **Send test card to watch** in StarPath first if StarPath does not appear in the app list yet). Prefer Gadgetbridge? Pair the watch there instead and enable **StarPath** under Notifications. See `docs/COMPATIBILITY.md` for the one-app rule — both installed, or neither, blocks delivery.
4. **Ride with Google Maps**:
   Start navigation in Google Maps. StarPath starts automatically in the background, delivers live turn updates and vibration pulses before intersections, and shuts itself down when you arrive.

> [!TIP]
> **Screen-off Alert Tip**: If your companion app has a "receive only when phone screen is off" option and it is enabled, it will not send notifications to the watch while your phone screen is turned on. Either turn that setting OFF for testing, or tap **"Send test card in 5s"** in StarPath and immediately lock your phone screen.

---

## ⌚ Compatibility

**Tested:** Amazfit Active 2 (Round) + Android phone — full turn/vibrate/wake verified.

**Expected to work:** any watch paired via the Zepp/Gadgetbridge App with notification/alert mirroring (Active, Balance, Bip, T-Rex, Cheetah, Falcon, GTR/GTS families).

The watch shows 5 marks (`◀◀` / `▶▶` / `▲▲` / `DEST` / `?` — see `docs/COMPATIBILITY.md` for the display model); if arrows show as boxes, enable **ASCII arrows** in StarPath (`<-` / `->` / `^` / `DEST` / `?`).

---

## 🛠️ How to Build

### Prerequisites
- JDK 17+
- Android SDK with platform `android-36` (Android 16) + build-tools `36.0.0` (`export ANDROID_HOME=~/Android/Sdk` or configure `local.properties`)

### Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Build signed release APK (needs release signing; CI-only — see AGENTS.md)
./gradlew :app:assembleRelease

# Run unit tests
./gradlew :app:testDebugUnitTest
```

Output artifacts:
- Local debug build: `app/build/outputs/apk/debug/app-debug.apk` (the `versioned/starpath-v*.apk` copy only happens on CI — see `app/build.gradle.kts`).
- CI release build: `app/build/outputs/apk/versioned/starpath-v*.apk`.

---

## 📚 Documentation

- **[docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)** — Architectural diagrams, component roles, stay-awake pulse algorithms, and developer debugging steps.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — Development setup, notification capture guide, and PR guidelines.
- **[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)** — Which watches work, the 3-direction display model, and how to report your model.
- **[docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md)** — Privacy policy and data handling (100% on-device, zero telemetry).
- **[docs/blog-watch-missed-turn.md](docs/blog-watch-missed-turn.md)** — Blog post: how arrow-pixel recognition learned to read Maps turn icons.

---

## ⚠️ Safety

Glance only — never interact with your watch while riding or driving.
