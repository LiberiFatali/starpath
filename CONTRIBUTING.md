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

## 🗺️ Fixing Parsing (English Phrasing Variants)

Google Maps notification wording varies slightly across regions. Text parsing is English-only (set the phone locale to English; street names pass through verbatim) — the icon classifier covers icon-only / non-English frames language-free. See `docs/IMPLEMENTATION.md §5` for the canonical decision order (confident icon verdict wins over text) and `docs/IMPLEMENTATION.md §4` for the canonical capture steps. Most contributions involve updating `GMapsParser.kt` for new English phrasing.

### 1. Capture Raw Notification Dumps

Canonical steps live in `docs/IMPLEMENTATION.md §4` (no adb needed: StarPath → **Last Maps notification** → **Copy/Share debug info**). With USB debugging, alternatively:
```bash
adb logcat -s StarPath
adb shell dumpsys notification --noredact | grep -A 30 "com.google.android.apps.maps"
```

### 2. Add Keywords to Parser
Edit [`app/src/main/java/app/starpath/nav/parse/GMapsParser.kt`](app/src/main/java/app/starpath/nav/parse/GMapsParser.kt):
- Add English maneuver phrasing to the existing guards (e.g. turn/continue/head-straight variants). Do not add non-English keywords — text parsing is English-only by design; non-English frames defer to the icon classifier.
- Do not add next-turn distance parsing — it cannot be extracted reliably from the Maps notification (only the trip line's *remaining* distance is read).

### 3. Add Unit Tests
Add your captured notification text to [`app/src/test/java/app/starpath/nav/parse/GMapsParserTest.kt`](app/src/test/java/app/starpath/nav/parse/GMapsParserTest.kt):
```kotlin
@Test
fun `parses english phrasing variant correctly`() {
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
   git checkout -b feat/parse-english-variant
   ```
2. Commit your changes with clear commit messages following Conventional Commits format (e.g. `feat: ...`, `fix: ...`, `docs: ...`).
3. Push to your fork and submit a Pull Request to the `main` branch.
4. In your PR description, include:
   - Summary of the changes.
   - Example notification strings tested.
   - Confirmation that `./gradlew :app:testDebugUnitTest` passes.

---

## 🏷️ Releasing a New Version

Releases are cut ONLY via the **Release** workflow (Actions → Release → Run workflow on branch `main`, optional `version` input — empty auto-bumps minor). The workflow header in `.github/workflows/release.yml` is canonical for the version scheme (`0.MINOR` / `MINOR`), tag format, and bot-owned files — do not duplicate its mechanics here.

What contributors need to know:

- Direct `git tag` pushes are inert — no workflow listens to them. Use the Release workflow.
- Never hand-edit the bot-owned outputs: version literals in `app/build.gradle.kts`, the `SECURITY.md` supported-version row, `fastlane/metadata/android/en-US/changelogs/<code>.txt`, or the `fdroid/app.starpath.yml` sync-back (`Builds` / `CurrentVersion*`). (`fdroid/app.starpath.yml` is otherwise hand-editable; keep `Builds` / `CurrentVersion*` plain literals in sync.)
- Tag format is `v0.<minor>` (patch/major rejected until the versionCode scheme migrates).
- Local builds use the checked-in version literals (kept literal so F-Droid's checkupdates parser can read them).

---

## 📜 License Reference

By contributing to StarPath, you agree that your contributions will be licensed under the project's [GNU General Public License v3.0](LICENSE).
