# HOISLY v9.1 — Adaptive Glass Source Patch

## What changed

1. `WallpaperBlur` no longer treats `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` as proof that `WallpaperManager` is readable. It probes the actual wallpaper API and catches OEM/platform `SecurityException`s.
2. Added a persisted, user-selected wallpaper image source using Android's Storage Access Framework (`OpenDocument`). This does not require broad storage permission or `MANAGE_EXTERNAL_STORAGE`.
3. `MainActivity` and `SettingsActivity` now open the system image picker when the user asks to grant/fix wallpaper access. A selected image is saved as the fallback source and glass refresh is requested.
4. Wallpaper loading tries the system wallpaper first; if the platform denies access or returns no drawable, it decodes the explicitly selected fallback image and uses the existing blur/sharp/body pipeline.
5. Access status now checks the actual wallpaper API or the persisted fallback source, rather than inferring access from a media permission.

## Important behavior / limitation

Android does not provide a universal guarantee that a third-party launcher can read the current system wallpaper bitmap on every Android version and OEM ROM. The fallback is therefore an image the user explicitly chooses. When the system API is available, the launcher still uses the live system wallpaper first.

## Not yet verified

A Gradle compile and device matrix test have NOT completed in this environment. The Gradle wrapper could not download Gradle 9.8.1 because network/DNS access to `services.gradle.org` was unavailable. This source patch must be compiled and tested on a development machine and then tested on real devices (including Samsung API 35 and TECNO Android 14) before calling it release-ready.

## Device test checklist

- Android 13 / 14 / 15 / 16 / 17 where available.
- Samsung, TECNO/Infinix, Xiaomi/Redmi, OPPO/Realme, Vivo.
- Test system wallpaper read success, `SecurityException`, live wallpaper, and selected-image fallback.
- Revoke selected-image permission in system settings and confirm diagnostics report the failure rather than crashing.
- Confirm shader compile and actual Liquid Glass draw stats independently from wallpaper-source status.
