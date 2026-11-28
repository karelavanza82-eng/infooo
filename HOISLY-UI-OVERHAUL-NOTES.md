# Hoisly: app-launch fix, settings overhaul and UI Lab

> Not compiled or run in the authoring environment (no Gradle/Kotlin toolchain, no network).
> Build it in Android Studio and report anything that fails.

## 1. App open/close animation
- `Motion.APP_LEAD_MS` 320 ms (scaled) -> 40 ms (fixed): the app starts right after the tap and its cold start runs in parallel with the animation.
- `MainActivity.onResume` closes a stranded full-screen surface even if `onStop` never ran.
- The shrink of a cancelled surface runs in its own scope (it used to be cancelled, leaving a half-grown surface on screen).
- The app icon stays on the surface (Default Hoisly and iOS 27) instead of fading into a flat colour.

## 2. Settings layout
- Every icon tile is translucent glass with a white glyph (no more colour coding). Bento tiles, hero card and the "finish setup" banner are colourless too.
- Cards follow the launcher's glass mode (Liquid or Frosted) instead of being forced to Frosted.
- Everything is bigger: row height 52 -> 66 dp, titles 17 -> 19 sp, large title 34 -> 42 sp, tiles 128/92/76 -> 152/116/100 dp.
- Info section: "Check updates", "About", "Version", "License" are full-width large tiles; the update dialog and About page use larger type.

## 3. UI Lab (Settings > UI Lab) - 10 new features
| # | Feature | Where it acts |
|---|---------|---------------|
| 1 | Dim wallpaper (0-70%) | darkens the Home background only |
| 2 | FPS meter | FPS + slowest frame, top-right of Home |
| 3 | Keep screen on at Home | `FLAG_KEEP_SCREEN_ON` while Home is in front |
| 4 | Home orientation (Portrait / Auto / Landscape) | `requestedOrientation` |
| 5 | Hide navigation bar | hides the nav bar on Home, swipe to peek |
| 6 | Settings text size (Normal / Large / Extra) | font scale of the settings app only |
| 7 | Settings background darkness | scrim behind settings pages (5-85%) |
| 8 | Copy look code | one `HOISLY-LOOK:...` line on the clipboard |
| 9 | Paste look code | applies a code (glass, icons, animation, dock, grid margins, Lab sliders) |
| 10 | Reset the whole look | defaults for the same keys; layout, hidden apps, names, custom icons stay |

All values are stored in the normal settings store, so backups include them.
