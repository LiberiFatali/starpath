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
Edit [`app/src/main/java/app/starpath/nav/GMapsParser.kt`](app/src/main/java/app/starpath/nav/GMapsParser.kt):
- Add maneuver keywords to regex patterns (e.g. left/right/straight/roundabout keywords).
- Add distance units if your language uses localized symbols.

### 3. Add Unit Tests
Add your captured notification text to [`app/src/test/java/app/starpath/nav/GMapsParserTest.kt`](app/src/test/java/app/starpath/nav/GMapsParserTest.kt):
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

## 🏷️ Releasing a New Version

Releases are driven by git tags. The tag is the source of truth — no manual
version-bump commit is needed before tagging.

```bash
git checkout main
git pull
git tag v0.7
git push origin v0.7
```

The `Release` workflow then:

1. Derives `versionName` / `versionCode` from the tag (`v0.7` → name `0.7`,
   code `7`) and builds the signed APK/AAB with those values.
2. Creates the GitHub Release with checksums.
3. Syncs back to `main` (bot commit): a fastlane changelog stub at
   `fastlane/metadata/android/en-US/changelogs/<code>.txt` (only if missing)
   and a new `Builds` entry plus `CurrentVersion` fields in
   `fdroid/app.starpath.yml`.

Notes:

- Tag format is `v0.<minor>` (e.g. `v0.7`). Patch or major versions
  (e.g. `v0.7.1`, `v1.0`) are rejected until the versionCode scheme is
  migrated to a computed mapping.
- Local builds default to the checked-in fallback version; set
  `APP_VERSION_NAME` / `APP_VERSION_CODE` (or `-PappVersionName` /
  `-PappVersionCode`) to override.
- `workflow_dispatch` runs accept a `version` input instead of a tag, but
  skip the metadata sync-back.

---

## 📜 License Reference

By contributing to StarPath, you agree that your contributions will be licensed under the project's [GNU General Public License v3.0](LICENSE).
