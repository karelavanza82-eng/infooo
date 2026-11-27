# HOISLY v9.2 — ZFL Liquid Glass wallpaper-access integration

## Compatibility changes
- Ported the ZFL wallpaper access approach as one supported route, but removed the assumption that `MANAGE_EXTERNAL_STORAGE` is the only valid route to `WallpaperManager`.
- HOISLY now probes the actual `WallpaperManager` APIs first on each Android/OEM build and catches `SecurityException`, null drawables, and vendor-specific failures.
- `MANAGE_EXTERNAL_STORAGE` / All files access remains an optional route exposed from Settings for ROMs that require it; it is not treated as proof that wallpaper extraction succeeded.
- The Home screen still offers the system document picker. The selected image is persisted through a URI grant and used when system wallpaper extraction is unavailable.
- Access diagnostics now probe the actual wallpaper API and validate the persisted fallback URI rather than returning true merely because All files access is enabled.
- Live wallpapers use the selected-image fallback because the static bitmap exposed by WallpaperManager may not match the live rendered content.
- Preserved HOISLY's three glass modes, blur pipeline, luminance map, shader, refraction, highlights, and diagnostics.

## Expected behavior
1. Try `WallpaperManager.drawable` and `peekDrawable` without assuming a particular storage permission model.
2. If the ROM denies access or returns no usable wallpaper, show the system image picker fallback.
3. If the user has already selected a fallback image, reuse its persisted URI grant.
4. If neither route is available, retain the safe Frosted/tinted fallback instead of crashing.

## Verification status
- ZIP archive integrity: checked after packaging.
- Gradle compile: NOT VERIFIED; Gradle 9.8.1 distribution could not be downloaded in this environment (`UnknownHostException: services.gradle.org`).
- APK: NOT BUILT.
- Physical-device tests: NOT RUN. Test Android 12, 13, 14, and 15 across Samsung, Xiaomi/Redmi, OPPO/Realme, Vivo, and TECNO/Infinix, including devices with live wallpapers.

Do not describe this source as a tested release until compilation and device tests pass.
