# StarPath — Compatibility

StarPath is a phone-side bridge: it reads Google Maps navigation
notifications and re-posts them as a standard, non-ongoing Android
notification (`BigTextStyle`, high priority) that the Zepp App forwards
over BLE to the paired watch. There is no watch-specific code and no
watch app to install.

## Tested

- **Amazfit Active 2 (Round)** + Android phone — full turn / vibration /
  screen-wake verified.

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

Small or square screens may wrap the street/trip lines; the title
(direction + distance) always fits. The trip line is tagged `DEST`
(e.g. `DEST 450 m · 6 min`) so the two distances never blur; the final
arrival card shows bare `DEST` with no trip line.

## Out of scope / untested

- Bands paired via Zepp Life / Mi Fitness (different companion app).
- iOS (StarPath is Android-only).

## Report your model

Model + Zepp App version + arrows OK? + wake/vibrate OK? Paste it with
StarPath's **Share debug info** output in a GitHub issue.
