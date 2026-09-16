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
│                            │  ArrowBitmapGenerator │   │
│                            │   (High-Contrast UI)  │   │
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
* **File:** `app/src/main/java/app/starpath/nav/StarPathListener.kt`
* **Role:** Extends Android's `NotificationListenerService`.
* **Behavior:**
  - Filters strictly for notifications originating from `com.google.android.apps.maps` with `FLAG_ONGOING_EVENT`.
  - Extracts the raw notification extras (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`).
  - Automatically spins up `KeepAliveService` when an active navigation session starts.
  - Automatically cancels StarPath cards and tears down `KeepAliveService` when Google Maps navigation ends or is dismissed.

### `GMapsParser`
* **File:** `app/src/main/java/app/starpath/nav/GMapsParser.kt`
* **Role:** Pure functional parser that translates Google Maps notification text into structured `NavUpdate` objects.
* **Capabilities:**
  - **Maneuver Detection:** Identifies turns (left, right, slight, sharp), U-turns, highway ramps/exits, roundabouts, and arrivals.
  - **Bilingual Parsing:** Supports English (`Turn left`, `In 200 m`, `Head north`) and Vietnamese (`Rẽ trái`, `Đi về hướng`, `Nhập vào`).
  - **Distance Extraction:** Parses meters, kilometers, feet, and miles, standardizing them into an integer `distanceMeters` for milestone calculations.
  - **Status States:** Identifies rerouting states, searching for GPS, and final arrival.

### `NavAlertManager`
* **File:** `app/src/main/java/app/starpath/nav/NavAlertManager.kt`
* **Role:** Decides whether an incoming update should trigger an alert (vibrate & wake the watch display) or remain silent.
* **Logic:**
  1. **Maneuver Change:** Always triggers an alert when the requested action changes (e.g. `STRAIGHT` -> `TURN_LEFT`).
  2. **Milestone Crossed:** Triggers an alert when approaching a turn and crossing key distance thresholds:
     - `500 m` → `200 m` → `100 m` → `50 m`
  3. **Stay-Awake Pulse:** If within `300 m` of an active turn and the watch screen has likely turned off (elapsed time ≥ 18s), sends a gentle alert pulse to refresh the card on the watch screen.
  4. **Cruising Straight:** Suppresses milestone alerts while maintaining a long straight path to prevent unnecessary vibrations and battery drain.

### `ArrowBitmapGenerator`
* **File:** `app/src/main/java/app/starpath/nav/ArrowBitmapGenerator.kt`
* **Role:** Programmatically renders large, high-contrast directional icons onto a 128×128 `Bitmap`.
* **Display Optimization:**
  - Android notification icons are often scaled down or masked on circular smartwatches.
  - By rendering bold geometric arrow glyphs on a high-contrast background (dark circle with bright green/white arrows) and attaching it as the notification's `largeIcon`, the watch display renders an instantly legible directional indicator even at a glance while riding.

### `KeepAliveService`
* **File:** `app/src/main/java/app/starpath/nav/KeepAliveService.kt`
* **Role:** Android foreground service configured with `foregroundServiceType="specialUse"`.
* **Purpose:** Prevents Android's aggressive background battery manager from killing `StarPathListener` while the phone screen is locked in a pocket during a ride. Includes a direct "Stop" action on its ongoing notification for easy termination.

### `MainActivity`
* **File:** `app/src/main/java/app/starpath/ui/MainActivity.kt`
* **Role:** Setup and onboarding UI.
* **Features:**
  - Chained 1-tap onboarding (Notification permission -> Battery optimization exemption -> Notification Listener settings).
  - Test card dispatchers: immediate test card and a 5-second delayed test card (allowing the user to lock their phone to test Zepp screen-off mirroring).

---

## 3. Smartwatch & Zepp Bridge Mechanics

StarPath does not require a custom mini-program installed on the watch for v1. Instead, it relies on the companion app's native notification bridge:

1. StarPath posts an Android notification on the phone with `PRIORITY_HIGH` and `CATEGORY_NAVIGATION`.
2. The **Zepp App** detects the notification via its own notification reader and transmits the title, text, and icons over Bluetooth Low Energy (BLE) to the Amazfit Active 2.
3. The watch vibrates and turns on its screen, presenting the glance card.

### Watch Display Lifespan
Smartwatches typically shut off their screen after 5 to 10 seconds to conserve battery:
* **Recommended Watch Setting:** In watch **Settings** → **Display** → **Screen-on Duration** (or **Auto Screen Off**), set to **15s – 30s**.
* **Automated Wakeup:** StarPath's `NavAlertManager` milestone events and 18s approach pulse ensure the watch wakes up automatically as you approach intersections without needing to touch the watch.

---

## 4. Debugging & Testing Google Maps Parsing

If Google updates the Google Maps notification format in your region or language, follow these steps to capture and fix the parser:

### Step 1: Capture Raw Notification Dump
Connect your phone via USB with USB debugging enabled, start Google Maps navigation, and run:
```bash
adb logcat -s StarPath StarPathRemote
```
In debug builds `StarPathListener` logs the extras (`title/text/bigText/lines`)
on every Maps post. Alternatively, inspect the active notification extras using `dumpsys`:
```bash
adb shell dumpsys notification --noredact | grep -A 30 "com.google.android.apps.maps"
```

> Why RemoteViews first: Maps keeps the true instruction in its custom
> layout (`nav_description` = e.g. "Turn left onto X", `nav_title` =
> distance, `nav_time` = trip line, arrow in `nav_notification_icon`) —
> see `MapsRemoteParser.kt` (same approach as `3v1n0/GMapsParser`).
> `GMapsParser` extras parsing is only the fallback, and unparsable
> directions render as `?`, never as a fake straight arrow.

### Step 2: Add a Test Case
Open `app/src/test/java/app/starpath/nav/GMapsParserTest.kt` and add a unit test using the captured raw strings:
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
