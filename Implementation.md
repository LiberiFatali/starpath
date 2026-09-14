★ StarPath MVP is built and verified — starpath/app-debug.apk (8 unit tests pass, BUILD SUCCESSFUL).
What you got (/home/hieunm/Projects/SideProj/starpath/):
- StarPathListener — reads Google Maps' live notification, parses maneuver/distance/street/ETA (EN + VI), re-posts a glanceable card (⬅ 200 m / Nguyen Hue)
- Vibrate-on-turn only (maneuver change alerts; ETA refreshes are silent), reroute + arrival auto-clear, keep-alive service, 5-step onboarding with a test card button
- Latest versions throughout: SDK 36, AGP 9.4.0 (built-in Kotlin), Kotlin 2.4.20, Gradle 9.6.0, core-ktx 1.18.0 (1.19.0 needs the unreleased SDK 37 — documented in README)
About installing it "into the watch" — important, as planned: v1 installs only on the phone (adb install -r app-debug.apk). Nothing installs on the Active 2; the watch shows StarPath's cards via Zepp App → Profile → watch → App Alerts → enable StarPath. The README has the full 4-step watch setup. A custom on-watch Mini Program is the deferred v2 (needs a BLE-bridge spike first).
Two things needed from you to road-test: plug in your Android phone (adb is at ~/Android/Sdk/platform-tools/) and your Zepp App version. Next step when you're ready: bicycle test, then the v2 custom round-UI spike if the stock notification card feels too small at speed.
