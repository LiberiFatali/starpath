# AGENTS.md — StarPath agent guardrails

Essential constraints only. Canonical details live in the linked files;
do not duplicate them here.

## Build / test

- Prereqs: JDK 17, Android `android-36` + build-tools `36.0.0`,
  `ANDROID_HOME` or `local.properties`.
- Commands: see `README.md § How to Build`. Parser workflow (capture →
  test → verify): see `docs/IMPLEMENTATION.md §4–5` (canonical) and
  `CONTRIBUTING.md § Fixing Parsing`.
- After nav/parser changes, run `./gradlew :app:testDebugUnitTest`.
- Never commit `*.apk` / `*.aab` / `*.jks`, `local.properties`,
  or `keystore.properties` (all gitignored). Never ask for signing secrets;
  release signing is CI-only and optional locally (`hasReleaseSigning`).

## Release — do not touch

- Releases are cut ONLY via Actions → Release (`workflow_dispatch` on
  `main`). Direct `git tag` pushes are inert. Mechanics (scheme, tag
  format, guards) live in `.github/workflows/release.yml` (canonical) —
  see also `CONTRIBUTING.md § Releasing a New Version` for the human pointer.
- Agent must never: bump versions, create/move tags, or hand-edit
  `fastlane/metadata/android/en-US/changelogs/<code>.txt`, or the
  `SECURITY.md` version row. The workflow's bot commit (`[skip ci]`) owns
  all of these. (`fdroid/app.starpath.yml` is hand-editable; keep
  `Builds` / `CurrentVersion*` plain literals in sync.)
- Past incident: deriving the version from env/`gradleProperty` (Elvis
  chain) made F-Droid `checkupdates` report `version=None` and fail with
  `current version is newer: old vercode=6, new vercode=5`.

## F-Droid literal constraint

- `app/build.gradle.kts` must keep 4 plain literals in sync:
  `val appVersionCode`, `val appVersionName`, and `defaultConfig`
  `versionCode` / `versionName`. Never derive them from env, gradle
  properties, or git tags — F-Droid's parser reads literals only.
- Version scheme and bump mechanics are defined in `release.yml`; do not
  restate them here.

## Architecture guardrails

See `docs/IMPLEMENTATION.md` (canonical) and `docs/PRIVACY_POLICY.md`.

- Zero-network, on-device, ephemeral processing. No analytics, trackers,
  logging of personal data, or persistent storage of notification content.
- The watch shows notification **text glyphs only**
  (`◀◀` / `▶▶` / `▲▲` / `DEST` / `?`; canonical display model:
  `docs/COMPATIBILITY.md`). `largeIcon` bitmaps do not reach the
  Amazfit Active 2 — never try to fix watch directions via images.
- Decision order: extras text parsed, then a confident `IconClassifier`
  verdict wins over text (`MapsRemoteParser`; canonical:
  `docs/IMPLEMENTATION.md §5`) → `UNKNOWN` renders as `?`, never a
  fake straight arrow. `subText` feeds the trip line only, never distance.
  No RemoteViews parsing.
- `KeepAliveService` (`specialUse`) starts/tears down with Maps navigation
  automatically. Keep background work minimal (battery, wake locks,
  allocations).
