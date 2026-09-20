# StarPath — Compatibility

StarPath is a phone-side bridge: it reads Google Maps navigation
notifications and delivers them as turn cards to your watch through
**exactly one** companion app — either the Zepp App or Gadgetbridge.
There is no watch-specific code and no watch app to install.

## One-app rule (required)

Keep **only one** watch app installed: either Zepp **or** Gadgetbridge.
If both are installed — or neither — StarPath blocks all delivery and
posts a one-off "StarPath blocked" notice (tap it to open StarPath).
Two feeders fight over the watch's single Bluetooth link (reconnect loop
observed Sep 2026), so "both on" is unsupported by design, not just
untested.

## Supported setups

|  | Zepp-only | Gadgetbridge-only |
|---|---|---|
| Phone app | Zepp App (`com.huami.watch.hmwatchmanager`) | Gadgetbridge (`nodomain.freeyourgadget.gadgetbridge`) |
| Watch setting | Notification/alert mirroring ON, StarPath selected (send a test card first if StarPath isn't listed yet) | Pebble Messages → **Always** |
| Arrows | `◀◀` / `▶▶` / `▲▲` / `DEST` / `?` (ASCII `<-` / `->` / `^` / `DEST` / `?` fallback in StarPath) | Same card, same marks |
| Vibration / wake | Verified on Active 2 | Verified on Active 2 (Sep 2026: PebbleKit `PEBBLE_ALERT` broadcast) |
| Status | Stable default, fully supported | Supported extra path |

## Tested

- **Amazfit Active 2 (Round)** + Android phone — full turn / vibration /
  screen-wake verified on **both** paths (Zepp; Gadgetbridge 0.94.0).

## Expected to work

Any watch paired via the **Zepp App** with notification/alert mirroring
enabled (Active, Balance, Bip, T-Rex, Cheetah, Falcon, GTR/GTS families).
Setup idea is the same everywhere: open the paired watch in the Zepp App,
enable notification mirroring, select StarPath (send a test card first if
StarPath isn't listed yet), keep the watch connected over Bluetooth with
Do Not Disturb off.

## Display model

The watch shows 3 essential directions:

- `◀◀` left, `▶▶` right, `▲▲` straight, `DEST` destination, `?` otherwise (U-turn,
  roundabout/exit text without a side, unknown — the street text still carries
  the detail, e.g. "Make a U-turn…").
- If arrows show as boxes on your watch, enable **ASCII arrows** in
  StarPath: `<-` / `->` / `^` / `DEST` / `?`.

Small or square screens may wrap the street/trip lines; the mark-only title
(direction mark) always fits. The trip line is tagged `DEST`
(e.g. `DEST 450 m · 6 min`) with the remaining-trip distance; the final
arrival card shows bare `DEST` with no trip line.

## Out of scope / untested

- Both apps installed at once ("both on") — blocked by StarPath, see above.
- Bands paired via Zepp Life / Mi Fitness (different companion app).
- iOS (StarPath is Android-only).

## Report your model

Model + Zepp App version + arrows OK? + wake/vibrate OK? Paste it with
StarPath's **Share debug info** output in a GitHub issue.
