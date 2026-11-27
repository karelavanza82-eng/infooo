# HOISLY v8.7 — Liquid Glass compatibility patch

## Changes
- Removed the `MANAGE_EXTERNAL_STORAGE` manifest permission. It is not an appropriate prerequisite for reading the active system wallpaper.
- Removed the hard gate that returned `null` from wallpaper rendering whenever All files access was not granted.
- Removed the unconditional rejection of live-wallpaper devices. HOISLY now asks `WallpaperManager` for its drawable and gracefully handles platforms that do not expose one.
- Existing UI/onboarding compatibility state now treats wallpaper access as not requiring a special storage permission, so users are not sent to the All files access settings page for Liquid Glass.

## Important limitation
This patch addresses a concrete wallpaper-loading blocker in the supplied source. It does not prove that every vendor GPU can compile and render the AGSL shader. Android 13–17 device coverage still requires physical-device testing and shader error logs. If a device's `WallpaperManager` does not expose a drawable or its GPU rejects AGSL, the current renderer can still fall back to Frosted.

## Build verification
A Gradle test/build was attempted, but the environment could not resolve `services.gradle.org` (`UnknownHostException`). Therefore this ZIP is a source patch, not a verified APK. Build and test it with Android Studio/Gradle on a machine with the required Gradle distribution and Android dependencies cached or network access.
