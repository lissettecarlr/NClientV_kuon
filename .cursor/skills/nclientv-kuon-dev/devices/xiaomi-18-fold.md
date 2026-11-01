# Xiaomi 18 Fold (2608BPX34C, codename lhasa): screen data

Measured with adb on a real device (2026-11-01). The device is not a permanent test device: use this file and
`scripts/fold.ps1` (profiles `mi-*`) to test without it.

## Device

| | |
|---|---|
| Model | Xiaomi 18 Fold, `ro.product.model=2608BPX34C`, `ro.product.device=lhasa` |
| OS | HyperOS 3.0 / OS4.0.26.0.XPNCNXM, Android 17 (SDK 37) |
| Density | `ro.sf.lcd_density=440` (2.75x) on both screens |
| Font scale | 1.25 on the tested unit (user setting, makes titles wrap more) |
| Navigation | gesture (`navigation_mode=2`), gesture bar 55 px (20 dp) at the bottom |
| Hinge | `android.sensor.hinge_angle` present |
| Device states | CLOSED 0, TENT 1, HALF_OPENED 2, OPENED 3, OPENED_REVERSE 4, OPENED_PRESENTATION 5, OPENED_REVERSE_PRESENTATION 6 |
| Multi display | two physical displays, `screencap` needs `-d <id>` (see `scripts/shot.ps1`) |

## Screens

| | Outer (cover) | Inner (main) |
|---|---|---|
| SurfaceFlinger / display id | `4639175068132267009` (displayId 1, port 1) | `4639175402683733248` (displayId 0, port 0) |
| Resolution | 1168 x 1712 px | 1672 x 2364 px |
| Density | 440 dpi (385.3 x 385.2 real) | 440 dpi (381.9 x 382.0 real) |
| Size in dp, portrait | **425 x 623** (`sw425dp w425dp h623dp`) | **608 x 860** (`sw608dp w608dp h860dp`) |
| Size in dp, landscape | 623 x 425 | 860 x 608 (`sw608dp w860dp h608dp`) |
| screenLayout | `nrml` (normal) | `lrg` (large): changes when folding, needs `screenLayout` in configChanges |
| Aspect | 1:1.47 | 1:1.41 (about A4, not a tablet 3:4 or 4:3) |
| Refresh rate | 24-120 Hz (ARR) | 24-120 Hz (ARR) |
| Corner radius | 28 / 154 / 154 / 28 px (TL, TR, BR, BL) | 142 px, all four |
| Cutout (portrait) | top center, x 557-611, height 126 px (46 dp), status bar 126 px | top left, x 0-164, height 140 px (51 dp), status bar 140 px |
| Natural rotation | `installOrientation` 0 | `installOrientation` 180 (rotation values are shifted 180: portrait = ROTATION_0 with user_rotation 0) |

- Both screens report sw >= 425 dp. The inner one is above 600 dp, so Android 16+ ignores orientation/resizability
  restrictions of the app there (no letterboxing).
- Window insets seen by the app on the inner screen in landscape: statusBars 140 px top, navigationBars 55 px bottom (gesture), cutout 140 px top.
- Plain `screencap` prints a warning before the PNG when two displays exist, which corrupts the file.

## What the app does on it (checked on the real device, build of 2026-11-01)

| Case | Columns (main list) |
|---|---|
| Outer, portrait (425 dp) | 2 |
| Inner, portrait (608 dp) | 3 (`WindowColumns`: 2 * 608 / 411 = 2.96) |
| Inner, landscape (860 dp) | 4 |
| Outer, landscape (623 dp) | 4 (the landscape setting, never fewer than the setting) |

Rotating the inner screen with the reader open does not recreate the activity and leaves no dead strip.

## Testing on this device

- The system fold state can be forced with `adb shell cmd device_state state 0` (`state reset` to undo), but it only
  makes the device sleep / changes the recorded state: it does NOT switch the display. Do not rely on it.
- The cover screen cannot be driven by adb while the phone is open. Emulate it on the inner screen with the same
  dp: `adb shell wm size 1168x1712` (density stays 440 -> 425 x 623 dp, `nrml`), undo with `adb shell wm size reset`.
  `scripts/fold.ps1 real-outer` / `reset` does this.
- Force the inner screen orientation: `settings put system accelerometer_rotation 0; settings put system user_rotation 0` (portrait) or `1` (landscape).
  Restore: `accelerometer_rotation 1`, `user_rotation 0`.
- Real unit state before testing: `hide_multitasking` = true, `accelerometer_rotation` = 1, `user_rotation` = 0.
