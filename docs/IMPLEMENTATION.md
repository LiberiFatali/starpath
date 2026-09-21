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
│                            │   (Maneuver & Street) │   │
│                            └───────────┬───────────┘   │
│                                        │               │
│                                        ▼               │
│                            ┌───────────────────────┐   │
│                            │    NavAlertManager    │   │
│                            │ (Change & 30s stale   │   │
│                            │  reminder)            │   │
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
  - Filters strictly for notifications originating from `com.google.android.apps.maps` with `FLAG_ONGOING_EVENT` (navigation phase only — transient Maps pushes are dropped before parsing). Parsed updates additionally pass the `NavGate` validity check (rerouting, known maneuver, or icon verdict); non-navigation content such as crowdsource prompts never reaches the phone card or watch.
  - Extracts the raw notification extras (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`).
  - Automatically spins up `KeepAliveService` when an active navigation session starts.
  - Automatically cancels StarPath cards and tears down `KeepAliveService` when Google Maps navigation ends or is dismissed.

### `GMapsParser`
* **File:** `app/src/main/java/app/starpath/nav/parse/GMapsParser.kt`
* **Role:** Pure functional parser that translates Google Maps notification text into structured `NavUpdate` objects.
* **Capabilities:**
  - **Maneuver Detection:** Identifies turns (left, right, slight, sharp), U-turns, and arrivals. Roundabout/exit instructions carry no side for the watch and parse as `UNKNOWN` (`?`).
  - **English-Only Text Parsing:** Keyword matching covers English instruction phrasing only — set the phone locale to English so Maps emits English strings (see §5). Street names pass through verbatim (diacritics kept); direction for non-English/icon-only frames comes from the language-free `IconClassifier`.
  - **No Next-Turn Distance:** Next-turn distance is deliberately not parsed — it cannot be extracted reliably from the Maps notification, so nothing depends on it. Only the trip line's *remaining* distance is read (display-only, plus the far-from-arrival `DEST` plausibility guard).
  - **Status States:** Identifies rerouting states, searching for GPS, and final arrival.

### `NavAlertManager`
* **File:** `app/src/main/java/app/starpath/nav/runtime/NavAlertManager.kt`
* **Role:** Decides whether an incoming update should trigger an alert (vibrate & wake the watch display) or remain silent.
* **Logic (no distance — next-turn distance is unparseable):**
  1. **Maneuver Change:** Always triggers an alert when the requested action changes (e.g. `STRAIGHT` -> `TURN_LEFT`).
  2. **Rerouting:** Always triggers an alert on route recalculation.
  3. **Stale Reminder:** Triggers an alert when the same instruction (canonical direction + street + state) is unchanged for 30s, repeating every 30s until it changes — the reminder of the upcoming turn while approaching it. Applies to all maneuvers, including straight cruising.
* **Pipeline order:** `StarPathListener` evaluates the alert manager *before* the `NavDedup` check, so a stale reminder re-posts even though the instruction is dedup-identical; identical re-posts inside the 30s window stay silent.

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
3. The watch vibrates and turns on its screen, presenting the glance card (`▶▶ / street / DEST 450 m · 6 min`).

See `docs/COMPATIBILITY.md` for the supported-device model (any Zepp-App-paired watch with notification mirroring; tested on Amazfit Active 2).

The Gadgetbridge path uses the identical mirrored-card model: StarPath
posts the same phone notification and Gadgetbridge's notification
mirroring forwards it, so the nav-end `cancel()` retracts the watch card
on both paths (§6).

### Watch Display Lifespan
Smartwatches typically shut off their screen after 5 to 10 seconds to conserve battery:
* **Recommended Watch Setting:** In watch **Settings** → **Display** → **Screen-on Duration** (or **Auto Screen Off**), set to **15s – 30s**.
* **Automated Wakeup:** StarPath's `NavAlertManager` maneuver-change alerts and 30s stale-instruction reminders ensure the watch wakes up automatically as you approach intersections without needing to touch the watch.

---

## 4. Debugging & Testing Google Maps Parsing

If Google updates the Google Maps notification format in your region or language, follow these steps to capture and fix the parser:

### Step 1: Capture Raw Notification Dump (no adb needed)

Open StarPath → **Last Maps notification** → **Copy/Share debug info**. It shows,
for the most recent Maps post: winning source (`EXTRAS` vs `NONE`), parsed
maneuver/street/trip, all captured text fields, the icon-classifier
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

## 5. Direction Pipeline: Text Marks via Icon → Text → Unknown

**Constraint (verified Sep 2026).** The Amazfit Active 2 shows only notification *text*;
Maps often posts *icon-only* instructions (`40 m` + street name, no turn verb —
the arrow bitmap carries the direction). So neither text parsing alone nor
image forwarding can put turns on the wrist. StarPath bridges the gap on-phone:
it reads Maps' arrow **pixels** and re-emits the verdict as a **text mark**
(`◀◀`, or `<-` in ASCII mode).

**Decision order** (`StarPathListener` → `MapsRemoteParser.parseOutcome`):

1. **Icon match** (`IconClassifier`): always tried on ENROUTE frames.
   Confident verdict wins (language-free; immune to ambiguous phrasing like
   bare `toward X` — field case `turn_right_with_toward_text`: right-turn
   arrow with "toward …" text).
   Captures the notification large icon bitmap, binarizes by
   minority brightness polarity (works white-on-teal and dark-on-light),
   downscales to a 16×16 mask, recenters to the foreground bounding box, then
   compares first-order moments: `delta = topMeanX − bottomMeanX`
   (top-half mean-x minus bottom-half mean-x). `delta ≤ −1.5 → TURN_LEFT`,
    `delta ≥ +1.5 → TURN_RIGHT` (confidence `0.60 + |delta| × 0.07`, capped
     at `0.95`); a wide block (>8 cells) persisting to the icon's bottom
     edge — the pin+road largeIcon, both mirrors — → `DESTINATION` (`0.85`,
     renders as `DEST`); an enclosed background hole (donut loop, ≥4 cells)
     re-enables a lower `|delta| ≥ 1.0` threshold whose sign gives the
     roundabout exit side (`roundabout_right` → `▶▶` R≈0.70; its mirror →
     `◀◀`); a roundabout loop is wide mid-icon but tapers to a stem, so it
     never trips the DEST gate.
     A `DESTINATION` icon verdict is additionally suppressed when the trip
     line shows >500 m remaining (far-from-arrival lookalike → `?`);
     otherwise a centered
    mass with a narrow top apex → `STRAIGHT`
    (`0.80`), else `?` (never U-turn from
    pixels). Masks shorter than 8 rows (chevrons, lone heads) are `UNKNOWN`;
   Maps never points backwards, so there is no down-head. Thickness, dash
   style, and shift cancel out. The 16×16 mask and per-direction scores are
   logged to the `LastParse` debug dump for field harvesting.
2. **Text verb** (`GMapsParser`, English only): fallback for
   icon-missing/low-confidence frames; extras only
   (`title → bigText → text → textLines`;
   `subText` feeds the trip line only, never distance). Set the phone locale
   to English so Maps emits English strings; street names pass through
   verbatim (diacritics kept). Proven cases: `Turn left … → ◀◀`,
   `Head straight/up … → ▲▲`, bare `toward X → ?` and compass
   `Head north/south/… → ?` (both direction-neutral — the icon decides;
   turn/exit/keep/roundabout guards keep priority above them).
3. **`UNKNOWN → ?`**: never a fake straight arrow — the mark renders
    a distinct `?`. Display is `◀◀ / ▶▶ / ▲▲ / DEST / ?`
    (ASCII `<- / -> / ^ / DEST / ?`): left/right families
    (slight/sharp/keep) share the plain turn mark; the destination pin icon
    renders as `DEST`; U-turn and roundabout/exit text render as `?`
    (U-turn text is still detected by the parser
    — Maps does send it, rarely, text-only — but the notification carries no
    side, so the display stays `?`). Strict dedup skips re-posts with no
    direction/place/state change; the alert manager still re-posts on
    maneuver change, rerouting, or the 30s stale-instruction reminder.

**Field evidence** (user screenshots, Sep 2026): `Head south` + straight icon →
`▲▲` via icon (text yields `?`); icon-only `40 m` + street + left-hook → `◀◀` via moments;
`260 m` + street + thick right-hook → `▶▶ R=0.89 applied=true`
(`3_maps_starpath_turn_right.jpg`); `toward P. Nguyen Co Thach` + right-turn
arrow → `▶▶` via icon (text yields `?`, never fake-straight). Note: icon-only extras carry no
next-turn distance (trip line holds remaining distance), so these cards
render mark + street. Rerouting never latches: any live ENROUTE instruction
with a street clears a stale REROUTING card even when weak (`?` + street,
field: `no_text_rerouting`) — see `NavGate.shouldForward(update, appliedIcon, lastPosted)`.

**Files:** `nav/MapsRemoteParser.kt` (extras + largeIcon pixels + `Outcome`),
`nav/IconClassifier.kt` (pure, JVM-tested), `nav/GMapsParser.kt` (keyword
fallback), `nav/LastParse.kt` + `MainActivity` debug card (phone-only
diagnostics). Tests: `IconClassifierTest` (polarity/shift/noise/blank/thick-field-hooks),
`GMapsParserTest` (`toward`→`?` defer-to-icon, subText trip-only).

---

## 6. Delivery Paths: Zepp xor Gadgetbridge (One at a Time)

The parsed `NavFormatter.Card` reaches the watch through **exactly one**
companion app. `DeliveryPaths.resolve()` probes `PackageManager` for the
two known packages (`com.huami.watch.hmwatchmanager`,
`nodomain.freeyourgadget.gadgetbridge`) **once** — the choice is persisted
in `starpath_prefs` at first run and trusted afterwards, so the listener hot
path performs zero probes per Maps post. It is re-resolved on every
`MainActivity` open (opening the app is already part of every setup/debug
flow); installs/uninstalls in between take effect at the next open:

| Zepp | Gadgetbridge | Path |
|---|---|---|
| ✅ | ❌ | Zepp: `NavNotifier.post()` re-posts the phone card it mirrors (§3, unchanged) |
| ❌ | ✅ | Gadgetbridge: `NavNotifier.post()` re-posts the same phone card, which Gadgetbridge's notification mirroring forwards — nav end calls the same `cancel()`, so the watch clears exactly like Zepp |
| ✅ | ✅ | **Blocked** (`BLOCKED_BOTH`): two feeders fight over the watch's single BLE link (reconnect loop observed Sep 2026) |
| ❌ | ❌ | **Blocked** (`BLOCKED_NONE`) |

Blocked states start nothing: the `StarPathListener` gate sits **before**
parsing (no `KeepAliveService`), records the reason via
`LastParse.storeSkipped`, and posts one muted `starpath_setup`-channel
notice per Maps session (tap → `MainActivity`; auto-cancelled once exactly
one app is installed). `MainActivity` mirrors the state in its status line,
and both test-card buttons obey the same gate.

Manifest note: a `<queries>` block for both packages is required — without
it, `getPackageInfo` throws on API 30+ and every install reads as absent
(field catch, Sep 2026). No new permission. Watch-side prereq for the GB
path: enable StarPath under Gadgetbridge → Notifications, so the mirrored
card reaches the watch and the nav-end `cancel()` retracts it there.

**Files:** `nav/runtime/DeliveryPath.kt` (`DeliveryPaths.resolve`, JVM-tested
matrix). Tests: `DeliveryPathTest`.
See `docs/COMPATIBILITY.md` for the user-facing one-app rule.
