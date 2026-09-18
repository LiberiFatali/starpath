# StarPath — Implementation & Architecture Deep Dive

This document details the internal architecture, component responsibilities, alert logic, and debugging procedures for **StarPath**.

---

## 1. System Architecture & Data Flow

StarPath operates as a zero-network, local companion bridge between Google Maps navigation notifications and smartwatches (such as the Amazfit Active 2 running Zepp OS).

```
┌────────────────────────────────────────────────────────┐
│                      Android OS                        │
│                                                        │
│  ┌─────────────────┐       ┌────────────────────────┐  │
│  │   Google Maps   │       │    StarPathListener    │  │
│  │  (Navigation)   │──────>│ (NotificationListener) │  │
│  └─────────────────┘       └───────────┬────────────┘  │
│                                        │               │
│                                        ▼               │
│                            ┌───────────────────────┐   │
│                            │      GMapsParser      │   │
│                            │  (Maneuver & Distance)│   │
│                            └───────────┬───────────┘   │
│                                        │               │
│                                        ▼               │
│                            ┌───────────────────────┐   │
│                            │    NavAlertManager    │   │
│                            │  (Milestones & Pulse) │   │
│                            └───────────┬───────────┘   │
│                                        │               │
│                                        ▼               │
│                            ┌───────────────────────┐   │
│                            │      NavNotifier      │   │
│                            │  (Re-post Glance Card)│   │
│                            └───────────┬───────────┘   │
└────────────────────────────────────────┼───────────────┘
                                         │ BLE Notification Sync
                                         ▼
                             ┌───────────────────────┐
                             │       Zepp App        │
                             └───────────┬───────────┘
                                         │ Bluetooth Low Energy (BLE)
                                         ▼
                             ┌───────────────────────┐
                             │ Amazfit Active 2 Watch│
                             │ (Turn card on screen) │
                             └───────────────────────┘
```

---

## 2. Core Components

### `StarPathListener`
* **File:** `app/src/main/java/app/starpath/nav/runtime/StarPathListener.kt`
* **Role:** Extends Android's `NotificationListenerService`.
* **Behavior:**
  - Filters strictly for notifications originating from `com.google.android.apps.maps` with `FLAG_ONGOING_EVENT` (navigation phase only — transient Maps pushes are dropped before parsing). Parsed updates additionally pass the `NavGate` validity check (rerouting, known maneuver, icon verdict, or distance); non-navigation content such as crowdsource prompts never reaches the phone card or watch.
  - Extracts the raw notification extras (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`).
  - Automatically spins up `KeepAliveService` when an active navigation session starts.
  - Automatically cancels StarPath cards and tears down `KeepAliveService` when Google Maps navigation ends or is dismissed.

### `GMapsParser`
* **File:** `app/src/main/java/app/starpath/nav/parse/GMapsParser.kt`
* **Role:** Pure functional parser that translates Google Maps notification text into structured `NavUpdate` objects.
* **Capabilities:**
  - **Maneuver Detection:** Identifies turns (left, right, slight, sharp), U-turns, and arrivals. Roundabout/exit instructions carry no side for the watch and parse as `UNKNOWN` (`?`).
  - **Bilingual Parsing:** Supports English (`Turn left`, `In 200 m`, `Head north`) and Vietnamese (`Rẽ trái`, `Đi về hướng`, `Nhập vào`).
  - **Distance Extraction:** Parses meters, kilometers, feet, and miles, standardizing them into an integer `distanceMeters` for milestone calculations.
  - **Status States:** Identifies rerouting states, searching for GPS, and final arrival.

### `NavAlertManager`
* **File:** `app/src/main/java/app/starpath/nav/runtime/NavAlertManager.kt`
* **Role:** Decides whether an incoming update should trigger an alert (vibrate & wake the watch display) or remain silent.
* **Logic:**
  1. **Maneuver Change:** Always triggers an alert when the requested action changes (e.g. `STRAIGHT` -> `TURN_LEFT`).
  2. **Milestone Crossed:** Triggers an alert when approaching a turn and crossing key distance thresholds:
     - `500 m` → `200 m` → `100 m` → `50 m`
  3. **Stay-Awake Pulse:** If within `300 m` of an active turn and the watch screen has likely turned off (elapsed time ≥ 18s), sends a gentle alert pulse to refresh the card on the watch screen.
  4. **Cruising Straight:** Suppresses milestone alerts while maintaining a long straight path to prevent unnecessary vibrations and battery drain.

### `NavNotifier`
* **File:** `app/src/main/java/app/starpath/nav/runtime/NavNotifier.kt`
* **Role:** Re-posts the parsed state as a **text-only** glance card
  (`title`/`text`/`sub` with `◀◀`/`▶▶`/`▲▲`/`?`, or `<-`/`->`/`^`/`?` in
  ASCII mode; sub carries the destination remaining as `DEST 450 m · 6 min`,
  empty on arrival/rerouting). No `largeIcon`
  bitmap is attached: Zepp forwards only notification text to the
  paired watch (e.g. Amazfit Active 2), so outbound bitmaps were dead payload/CPU.
  Inbound Maps arrow pixels are still read via `MapsRemoteParser` +
  `IconClassifier` and re-emitted as marks (see §5).

### `KeepAliveService`
* **File:** `app/src/main/java/app/starpath/nav/runtime/KeepAliveService.kt`
* **Role:** Android foreground service configured with `foregroundServiceType="specialUse"`.
* **Purpose:** Prevents Android's aggressive background battery manager from killing `StarPathListener` while the phone screen is locked in a pocket during a ride. Includes a direct "Stop" action on its ongoing notification for easy termination.

### `MainActivity`
* **File:** `app/src/main/java/app/starpath/ui/MainActivity.kt`
* **Role:** Setup and onboarding UI.
* **Features:**
  - Chained 1-tap onboarding (Notification permission -> Battery optimization exemption -> Notification Listener settings).
  - Test card dispatchers: immediate test card and a 5-second delayed test card (allowing the user to lock their phone to test Zepp screen-off mirroring).
  - ASCII-arrows toggle (MainActivity only): `<- / -> / ^ / ?` fallback for watches missing arrow glyphs.

---

## 3. Smartwatch & Zepp Bridge Mechanics

StarPath does not require a custom mini-program installed on the watch for v1. Instead, it relies on the companion app's native notification bridge:

1. StarPath posts an Android notification on the phone with `PRIORITY_HIGH` and `CATEGORY_NAVIGATION`.
2. The **Zepp App** detects the notification via its own notification reader and transmits the notification **text** (title, body) over Bluetooth Low Energy (BLE) to the paired watch (e.g. Amazfit Active 2). **Images (`largeIcon`, bitmaps) are NOT forwarded** — verified Sep 2026: image forwarding on Amazfit is a 2026 iOS-only beta limited to newer watches (Cheetah 2 Ultra / Balance Ultra / Balance 3…), Amazfit Active 2 not included.
3. The watch vibrates and turns on its screen, presenting the glance card (`260 m ▶▶ / street / DEST 450 m · 6 min`).

See `docs/COMPATIBILITY.md` for the supported-device model (any Zepp-App-paired watch with notification mirroring; tested on Amazfit Active 2).

### Watch Display Lifespan
Smartwatches typically shut off their screen after 5 to 10 seconds to conserve battery:
* **Recommended Watch Setting:** In watch **Settings** → **Display** → **Screen-on Duration** (or **Auto Screen Off**), set to **15s – 30s**.
* **Automated Wakeup:** StarPath's `NavAlertManager` milestone events and 18s approach pulse ensure the watch wakes up automatically as you approach intersections without needing to touch the watch.

---

## 4. Debugging & Testing Google Maps Parsing

If Google updates the Google Maps notification format in your region or language, follow these steps to capture and fix the parser:

### Step 1: Capture Raw Notification Dump (no adb needed)

Open StarPath → **Last Maps notification** → **Copy/Share debug info**. It shows,
for the most recent Maps post: winning source (`EXTRAS` vs `NONE`), parsed
maneuver/distance/street/trip, all captured text fields, the icon-classifier
verdict + score, mask + per-direction scores, and any error. Paste it into the bug report.

With USB debugging, alternatively:
```bash
adb logcat -s StarPath
adb shell dumpsys notification --noredact | grep -A 30 "com.google.android.apps.maps"
```

> Unparsable directions render as `?`, never as a fake straight arrow
> (`GMapsParser` extras parsing is the only text source; no RemoteViews).

### Step 2: Add a Test Case
Open `app/src/test/java/app/starpath/nav/parse/GMapsParserTest.kt` and add a unit test using the captured raw strings:
```kotlin
@Test
fun `parses new format correctly`() {
    val update = GMapsParser.parse(
        title = "In 400 ft - Turn right",
        text = "Oak Street • ETA 5:30 PM",
        bigText = null,
        textLines = emptyList()
    )
    assertNotNull(update)
    assertEquals(NavManeuver.TURN_RIGHT, update?.maneuver)
    assertEquals("Oak Street", update?.street)
}
```

### Step 3: Run Tests
```bash
./gradlew :app:testDebugUnitTest
```
Ensure all tests pass before submitting changes.

---

## 5. Direction Pipeline: Text Marks via Text → Icon → Unknown

**Constraint (verified Sep 2026).** The Amazfit Active 2 shows only notification *text*;
Maps often posts *icon-only* instructions (`40 m` + street name, no turn verb —
the arrow bitmap carries the direction). So neither text parsing alone nor
image forwarding can put turns on the wrist. StarPath bridges the gap on-phone:
it reads Maps' arrow **pixels** and re-emits the verdict as a **text mark**
(`◀◀`, or `<-` in ASCII mode).

**Decision order** (`StarPathListener` → `MapsRemoteParser.parseOutcome`):

1. **Text verb** (`GMapsParser`): extras only
   (`title → bigText → text → textLines`;
   `subText` feeds the trip line only, never distance). Proven cases:
   `Head south → ▲▲`, `Turn left … → ◀◀`, bare `toward X → ▲▲` (straight
   phrasing; turn/exit/keep verbs keep priority above it).
2. **Icon match** (`IconClassifier`): only when text yields `UNKNOWN`.
   Captures the notification large icon bitmap, binarizes by
   minority brightness polarity (works white-on-teal and dark-on-light),
   downscales to a 16×16 mask, recenters to the foreground bounding box, then
   compares first-order moments: `delta = topMeanX − bottomMeanX`
   (top-half mean-x minus bottom-half mean-x). `delta ≤ −1.5 → TURN_LEFT`,
   `delta ≥ +1.5 → TURN_RIGHT` (confidence `0.60 + |delta| × 0.07`, capped
    at `0.95`); a wide bottom block (>8 cells — the pin+road largeIcon, both
    mirrors) → `DESTINATION` (`0.85`, renders as `DEST`); otherwise a centered
    mass with a narrow top apex → `STRAIGHT`
    (`0.80`), else `?` (never U-turn from
    pixels). Masks shorter than 8 rows (chevrons, lone heads) are `UNKNOWN`;
   Maps never points backwards, so there is no down-head. Thickness, dash
   style, and shift cancel out. The 16×16 mask and per-direction scores are
   logged to the `LastParse` debug dump for field harvesting.
3. **`UNKNOWN → ?`**: never a fake straight arrow — the mark renders
    a distinct `?`. Display is `◀◀ / ▶▶ / ▲▲ / DEST / ?`
    (ASCII `<- / -> / ^ / DEST / ?`): left/right families
    (slight/sharp/keep) share the plain turn mark; the destination pin icon
    renders as `DEST`; U-turn and roundabout/exit text render as `?`
    (U-turn text is still detected by the parser
    — Maps does send it, rarely, text-only — but the notification carries no
    side, so the display stays `?`). Strict dedup re-posts only on
    direction/place/state change, so 10–20 m countdown ticks are skipped.

**Field evidence** (user screenshots, Sep 2026): `Head south` + straight icon →
`▲▲` correct; icon-only `40 m` + street + left-hook → `◀◀` via moments;
`260 m` + street + thick right-hook → `▶▶ R=0.89 applied=true`
(`3_maps_starpath_turn_right.jpg`). Note: icon-only extras carry no
next-turn distance (trip line holds remaining distance), so these cards
render mark + street until distance is reworked.

**Files:** `nav/MapsRemoteParser.kt` (extras + largeIcon pixels + `Outcome`),
`nav/IconClassifier.kt` (pure, JVM-tested), `nav/GMapsParser.kt` (keyword
fallback), `nav/LastParse.kt` + `MainActivity` debug card (phone-only
diagnostics). Tests: `IconClassifierTest` (polarity/shift/noise/blank/thick-field-hooks),
`GMapsParserTest` (`toward` priority, subText trip-only).
