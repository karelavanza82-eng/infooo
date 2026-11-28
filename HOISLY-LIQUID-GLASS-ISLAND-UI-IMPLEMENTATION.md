# Hoisly Dynamic Island UI implementation

This source patch updates the Dynamic Island settings activity while keeping the existing settings repository, permissions, accessibility service, notification listener, and event routing intact.

## Included
- Uses Hoisly's existing `com.hoisly.launcher.ui.glass` renderer (`Modifier.glass`, `GlassProfile`, `GlassStyle`, `WallpaperBlur`, `LocalGlassSource`).
- Loads the current system wallpaper through the existing `WallpaperBlur` pipeline and uses it as the translucent page backdrop.
- Requests Liquid mode on Android 13+ when the existing wallpaper/shader inputs are available; retains the renderer's Frosted fallback on older supported Android versions or when wallpaper access is unavailable.
- Replaces the bottom four-item navigation with a vertical left rail for Beranda, Fitur, and Tampilan. Tentang is removed from primary navigation.
- Converts the shared settings cards, toggles, grouped rows, sliders, preview action, and home update/banner tiles to the same glass surface treatment.
- Keeps the active-island icon green, matching the charging icon.
- Charging: the compact island uses an iOS-style green battery body with the percentage inside and a white lightning bolt immediately to its right; no extra activity dot is shown while charging.
- Recording: existing activity rendering is retained; charging-specific activity dot is suppressed.
- Status bar: a crescent moon is rendered immediately left of the clock whenever Android Zen Mode / Do Not Disturb is active; state updates from the system interruption-filter broadcast.

## Safety scope
The accessibility service, notification listener, permission declarations, settings persistence, and event classification were not rewritten. The charging and recording edits are presentation/timing changes only.

## Build verification
A Gradle compile was attempted with `./gradlew :app:compileDebugKotlin --no-daemon`, but this environment could not resolve `services.gradle.org` to download the repository's Gradle 9.8.1 distribution (`UnknownHostException`). Therefore this archive is a source patch, not a verified build or APK. Run the compile/test tasks in an Android build environment with the required Gradle distribution and Android SDK before installing.
