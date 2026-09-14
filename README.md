# ★ StarPath — live Google Maps turns on your Amazfit Active 2

Personal Android app: reads the Google Maps navigation notification, reformats it
into a glanceable card (`◀◀ 200 m / Nguyen Hue`), and re-posts it with large direction
graphics. The **Zepp app** forwards that card over BLE to the watch. Phone stays in your pocket.

## Versions (pinned Sept 2026)

- compileSdk / targetSdk **36** (Android 16), minSdk 29
- AGP **9.4.0**, Gradle **9.6.0**, Kotlin **2.4.20**, JDK 17+
- androidx core-ktx **1.18.0** (1.19.0 needs compileSdk 37, not in stable channel yet), lifecycle-runtime-ktx **2.11.0**
- Zepp App **10.8.1+**, Active 2 firmware up to date (Zepp OS 5)

## Build

```bash
export ANDROID_HOME=~/Android/Sdk   # or create local.properties with sdk.dir=
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

APK outputs:
- Versioned: `app/build/outputs/apk/versioned/starpath-v0.2-debug.apk`
- Standard: `app/build/outputs/apk/debug/app-debug.apk`

Install: `adb install -r app/build/outputs/apk/versioned/starpath-v0.2-debug.apk`.

## Getting it on the watch (no watch-side install needed for v1)

1. Update Zepp App + Active 2 firmware. Pair the watch.
2. Open StarPath, complete steps 1–4:
   - **1. Allow notification access** (Listener)
   - **2. Allow StarPath notifications** (POST_NOTIFICATIONS)
   - **3. Disable battery optimization**
   - **4. Start background listener**
3. Tap **5. Send test card to watch** (or **5b. Send test card in 5s**).
4. Zepp App → Profile → Active 2 → **App Alerts / Notifications** → **Manage Apps** → ensure **StarPath** is checked.
5. Tap test card again → it should vibrate on the watch within seconds with a large green/white directional arrow badge.
6. Navigate in Google Maps. Turn cards appear on the watch live, incl. reroutes.

## Keeping notifications on screen longer

1. **Watch Screen-on Duration (Auto Screen Off)**:
   - On the watch: Press button/crown → **Settings** → **Display** (or **Display & Brightness**) → **Screen-on Duration** (or **Auto Screen Off**) → set to **15s – 30s**.
2. **Automated Milestone Alerts**:
   - StarPath automatically wakes the watch screen at key turn approach milestones: **500 m**, **200 m**, **100 m**, and **50 m**.
3. **Stay-Awake Pulse**:
   - When within 300m of a turn, StarPath sends a periodic alert pulse (every ~18s) to keep the notification card on screen while waiting at lights or riding slowly.

## Troubleshooting: Nothing shown on watch when tapping test card

1. **Zepp "Only receive when the screen is off" setting (Most Common)**:
   - In Zepp App → Profile → Active 2 → **App Alerts**, check if **"Only receive when the screen is off"** is enabled.
   - If enabled, Zepp intentionally drops all notifications forwarded while you are looking at your active phone screen.
   - **Fix**: Either toggle this setting **OFF** during testing, or tap **"5b. Send test card in 5s"** and immediately lock/turn off your phone screen.
2. **Notification permissions (Android 13+)**:
   - Check if the StarPath test notification appears in your phone's notification drawer. If it does not appear on the phone, tap **2. Allow StarPath notifications** to grant `POST_NOTIFICATIONS`.
3. **StarPath checked in Zepp App Alerts**:
   - In Zepp App → Profile → Active 2 → **App Alerts** → **Manage Apps**, make sure **StarPath** is toggled ON.
   - Note: Some Zepp versions only show an app in this list after it has posted at least one notification on the phone. Tap button 5 once, then check the Zepp list.
4. **Watch Do Not Disturb (DND) / Sleep Mode**:
   - Swipe down on the watch face and verify that **DND (moon icon)** and **Sleep Mode** are turned OFF.
5. **Bluetooth connection**:
   - Ensure the Zepp app shows the watch as "Connected" and synced.

## If parsing breaks (Google changed the layout)

1. In a debug build, `adb logcat` the Maps notification dump.
2. Fix `app/.../nav/GMapsParser.kt`, add the dump as a case in `GMapsParserTest.kt`.
3. `./gradlew :app:testDebugUnitTest`.

## Safety

Glance only — never interact with the watch while moving.
