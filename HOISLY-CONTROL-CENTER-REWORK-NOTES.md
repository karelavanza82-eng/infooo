# Hoisly Control Center rework — source patch

This patch modifies the supplied `hoisly-with-controlcenter.zip` project in place.

## Implemented in source

- Replaced the Control Center's separate frosted-glass modifier with Hoisly's existing `ui.glass` renderer and `GlassProfile.Panel`.
- Passed Hoisly's live `LauncherSettings`, `LocalGlassSource`, and `LocalGlassStyle` into the overlay, setup screen, and tile editor. The wallpaper preview is loaded as well so Liquid Glass can use the same sharp/refraction source as the launcher.
- Rebranded the permission/setup page from `OnePlus Control` to `Hoisly Control Center`, changed status pills to Hoisly-style green active indicators, and applied the Hoisly glass surface to permission rows.
- Added a separate Notification Center stage. A pull from the left half of the top gesture strip opens notifications; a pull from the right half opens the full Control Center. Two-finger pull still opens the full Control Center.
- Added live notification cards, clear-all, open, dismiss, snooze, app notification settings, and notification actions to the notification stage.
- Added notification listener reconnect handling and a second snapshot read after connection to reduce OEM timing-related empty notification lists.
- Changed the fixed-width-only shade scaling to fit both width and height and center the 360×600 reference layout, addressing the landscape oversizing/overflow.
- Added an in-Control-Center appearance sheet with persisted overall scale and independent tile-icon scale sliders, plus a direct shortcut to the tile arrangement editor.
- Lowered the gesture catcher to a 48dp top strip to reduce overlap with OEM pull-down zones.
- Lowered `minSdk` from 31 to 28 (Android 9) as requested previously. Android 9 uses the existing non-AGSL/frosted fallback where Liquid Glass shaders are unavailable.

## Not yet verified

A Gradle compile was attempted, but the wrapper could not download Gradle 9.8.1 because this environment cannot resolve `services.gradle.org` (`UnknownHostException`). No APK is claimed as built or tested. The source must still be compiled with Android Studio/Gradle and tested on-device, especially Android 9, Android 13+ Liquid Glass, OEM gesture interception, notification listener reconnection, and landscape layouts.

## Remaining work for full parity

- Per-tile drag/reorder still uses the existing dedicated tile-editor Activity, opened directly from the Control Center appearance sheet; a fully embedded drag editor is not implemented in this patch.
- Per-tile individual sizing and arbitrary column-count editing are not yet implemented; overall UI scale and tile-icon scale are adjustable.
- OEM-specific gesture arbitration cannot be guaranteed by source inspection alone and requires testing on the target phone.
