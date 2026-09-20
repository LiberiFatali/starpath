# Privacy Policy for StarPath

**Effective Date:** September 14, 2026

StarPath ("we", "our", or "the app") is an open-source Android companion utility designed to mirror Google Maps turn-by-turn navigation directions to smartwatches (such as the Amazfit Active 2 via the Zepp app).

We believe in complete transparency and minimal data footprint.

---

## 1. Information Processing & Permissions

StarPath requires specific Android permissions to function as intended. All data is processed **locally on your device in real time**:

### A. Notification Access (`NotificationListenerService`)
* **Purpose**: StarPath observes notifications posted by Google Maps while you are actively navigating to extract the current turn maneuver, road name, and remaining-trip info.
* **Scope**: StarPath strictly filters for Google Maps navigation updates. It does not read, log, or inspect notifications from any other application.
* **Storage & Transmission**: No notification content is saved to persistent storage, uploaded to any remote server, or shared with third parties. Processing is strictly ephemeral (in-memory).

### B. Foreground Service (`KeepAliveService`)
* **Purpose**: Ensures that the turn processing service remains active in the background while your phone screen is turned off or locked during a ride.
* **Scope**: Only runs when active Google Maps navigation is detected, and automatically shuts down when navigation terminates or when dismissed.

### C. Battery Optimization Exemption
* **Purpose**: Prevents the Android operating system from aggressively killing the listener service during long rides with the screen locked.

---

## 2. Data Collection and Sharing

* **No Personal Data Collected**: StarPath does not collect, track, or log any personal information, device identifiers, or GPS coordinates.
* **No Analytics or Trackers**: The application contains no tracking libraries, telemetry SDKs, or third-party advertising frameworks.
* **No Network Transmission**: StarPath does not communicate with any external servers. All operations happen strictly between your phone and your watch companion app (via Android's native notification bridge).

---

## 3. Third-Party Services

StarPath interacts locally with:
* **Google Maps**: To read navigation updates while you navigate.
* **Companion Watch Apps (e.g. Zepp App)**: By posting a clean Android notification card, which your companion watch app forwards over Bluetooth Low Energy (BLE) to your paired smartwatch.

Please refer to Google's and your smartwatch companion app's respective privacy policies for how they handle notifications forwarded over Bluetooth.

---

## 4. Open Source

StarPath is open-source software. You can audit the complete source code at any time.

---

## 5. Contact

If you have questions or suggestions regarding this privacy policy, please open an issue on the GitHub repository:
[https://github.com/LiberiFatali/starpath/issues](https://github.com/LiberiFatali/starpath/issues)
