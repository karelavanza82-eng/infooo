# Hoisly iOS-style Control Center implementation

## What changed in this source snapshot

- Replaced the old Hoisly `ControlCenterPanel` UI implementation in place. `PanelHost` still routes the existing Control Center gesture to this one panel; no second overlay service or second application was added.
- Rebuilt the panel hierarchy around the supplied iOS reference: full-width media card, connectivity group, vertical brightness/volume faders, two wide focus/power controls, and a four-column circular shortcut grid.
- All panel surfaces and controls use Hoisly's existing `Modifier.glass(...)` renderer and `GlassProfile` definitions. The root `MainActivity` already provides `LocalGlassSource` and `LocalGlassStyle`; the panel therefore reuses the existing Hoisly Liquid Glass pipeline when the configured glass source is available, with the renderer's supported fallback otherwise.
- Kept existing Hoisly media-session, brightness, volume, flashlight, rotation, Focus/DND, Wi-Fi/Bluetooth/internet settings, calculator, camera, and panel-dismiss integrations.
- Added a media progress track using the active media session's playback position and metadata duration.
- Android-restricted features are not falsely represented as implemented: screen recording, QR scanning, and music recognition show an explanatory message. Screen mirroring opens Android Cast settings; the AirDrop-style action uses Android's native share sheet.
- `PanelHost`, notification center routing, launcher identity, signing configuration, and unrelated launcher features were not replaced.

## QuickSettings source audit / integration boundary

The supplied QuickSettings archive is a separate Android application with its own `com.example` package tree, overlay/accessibility/notification services, Room database, tile registry, editor/customizer screens, and application manifest. It is not safe to copy its entire app module into Hoisly as-is: it would introduce a second overlay/service lifecycle and duplicate app-level components. This source snapshot uses Hoisly's existing panel host and supported control actions rather than installing that standalone app as a second runtime.

This means the standalone QuickSettings app's complete preferences/editor/database/service feature set has **not** been ported in this snapshot. The delivered work is the replacement Hoisly Control Center UI and its available native operations, not a claim that every standalone QuickSettings settings screen has been merged.

## Production identity invariants checked

- `namespace`: `com.hoisly.launcher`
- `applicationId`: `com.hoisly.launcher`
- Release signing environment variable names remain unchanged.
- `APP_LEAD_MS`: `300`

## Build verification

A Gradle Kotlin compile was attempted with the repository wrapper. The environment could not resolve `services.gradle.org` to download the configured Gradle distribution (`UnknownHostException`), so a compile result is unavailable. Do not treat this archive as a verified APK build until CI or Android Studio completes a build.
