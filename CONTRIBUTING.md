# Contributing to StarPath

Thank you for your interest in contributing to **StarPath**! We welcome bug reports, feature suggestions, parser improvements, and new language translations.

---

## 🧭 Code of Conduct & Principles

1. **Safety First**: StarPath is designed for glanceable navigation while riding or walking. We do not accept features that encourage interaction with the watch while moving.
2. **Privacy First**: StarPath does not collect analytics, logs, or track user data. All processing must remain strictly on-device, in-memory, and ephemeral.
3. **Keep It Lightweight**: StarPath runs silently in the background. Minimize allocations, wake locks, and battery consumption.

---

## 🛠️ Development Setup

### Prerequisites
- JDK 17 or higher
- Android SDK Platform `android-36` (Android 16)
- Android SDK Build-Tools `36.0.0`
- `ANDROID_HOME` or `local.properties` pointing to your Android SDK

### Building the Project
```bash
# Clone your fork
git clone https://github.com/<your-username>/starpath.git
cd starpath

# Build debug APK
./gradlew :app:assembleDebug

# Run unit test suite
./gradlew :app:testDebugUnitTest
```

---

## 🗺️ Adding a New Language or Fixing Parsing

Google Maps notifications vary slightly across regions and languages. Most contributions involve updating `GMapsParser.kt`.

### 1. Capture Raw Notification Dumps
Connect your phone with USB debugging enabled, start Google Maps navigation, and capture the raw notification payload:
```bash
adb logcat -s StarPathListener GMapsParser
```
Or view the full extras via dumpsys:
```bash
adb shell dumpsys notification --noredact | grep -A 30 "com.google.android.apps.maps"
```

### 2. Add Keywords to Parser
Edit [`app/src/main/java/app/starpath/nav/GMapsParser.kt`](file:///home/hieunm/Projects/SideProj/starpath/app/src/main/java/app/starpath/nav/GMapsParser.kt):
- Add maneuver keywords to regex patterns (e.g. left/right/straight/roundabout keywords).
- Add distance units if your language uses localized symbols.

### 3. Add Unit Tests
Add your captured notification text to [`app/src/test/java/app/starpath/nav/GMapsParserTest.kt`](file:///home/hieunm/Projects/SideProj/starpath/app/src/test/java/app/starpath/nav/GMapsParserTest.kt):
```kotlin
@Test
fun `parses localized maneuver correctly`() {
    val update = GMapsParser.parse(
        title = "In 300 m - Turn left",
        text = "Main Street • ETA 10:45 AM",
        bigText = null,
        textLines = emptyList()
    )
    assertNotNull(update)
    assertEquals(NavManeuver.TURN_LEFT, update?.maneuver)
    assertEquals("Main Street", update?.street)
}
```

### 4. Verify All Tests Pass
```bash
./gradlew :app:testDebugUnitTest
```

---

## 🚀 Submitting a Pull Request

1. Create a descriptive feature branch:
   ```bash
   git checkout -b feat/add-spanish-support
   ```
2. Commit your changes with clear commit messages following Conventional Commits format (e.g. `feat: ...`, `fix: ...`, `docs: ...`).
3. Push to your fork and submit a Pull Request to the `main` branch.
4. In your PR description, include:
   - Summary of the changes.
   - Example notification strings tested.
   - Confirmation that `./gradlew :app:testDebugUnitTest` passes.

---

## 📜 License Reference

By contributing to StarPath, you agree that your contributions will be licensed under the project's [GNU General Public License v3.0](LICENSE).
