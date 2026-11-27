# HOISLY v9.2 — Three Glass Modes

## Implemented in source
- Added a distinct `WallpaperBlur` mode: it samples the blurred wallpaper image without the Frosted tint or glass rim.
- Kept `Frosted` as blurred wallpaper + adjustable tint + optional gradient edge.
- Kept `Liquid` as the AGSL/shader lens renderer, with its existing refraction/highlight/touch behavior.
- Added all three choices to Interface > Glass settings. Liquid is hidden on Android versions below API 33; Wallpaper Blur and Frosted remain selectable.
- Updated mode resolution so Wallpaper Blur and Frosted are not incorrectly rewritten to Frosted. Liquid falls back to Frosted only when API/shader support is unavailable.
- Kept the selected-image wallpaper fallback and system wallpaper access attempts already present in v9.1.

## Important limits / verification
- This is a source-level implementation. The project could not be compiled in this environment because Gradle 9.8.1 could not be downloaded (`UnknownHostException: services.gradle.org`).
- Device compatibility is not certified until APK builds and is tested on physical devices/OEM ROMs.
- Android may restrict automatic system-wallpaper reads. The user-selected image fallback is intentional and does not bypass Android security restrictions.
