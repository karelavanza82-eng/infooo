# Launchii app open/close motion integration

This update uses `hoisly-animation-styles-update-fixed.zip` as the Hoisly base and ports only the Launchii app-opening/app-closing motion into Hoisly's existing Compose transition pipeline.

## Launchii source reviewed

- `app/src/main/java/com/oneuiclone/home/ui/LaunchCard.kt` — icon-origin card geometry, home dim, icon growth, rounded-shape morph and retract behaviour.
- `app/src/main/java/com/oneuiclone/home/motion/Easing.kt` — damped spring interpolation used by the Zoom In launch card.
- `app/src/main/java/com/oneuiclone/home/Launcher.kt` — `zoomIn`, `retractLaunchCard`, and launch/return sequencing.

## Hoisly integration

- `app/src/main/java/com/hoisly/launcher/ui/home/LaunchiiAppMotion.kt` — app-only port of Launchii's Zoom In spring constants and geometry constants.
- `app/src/main/java/com/hoisly/launcher/ui/home/AppTransition.kt` — `IOS27` now draws the icon-origin zoom card and reverses it on return to Home. `HOISLY` keeps the existing Hoisly surface animation.
- `app/src/main/java/com/hoisly/launcher/ui/home/HomeScreen.kt` — iOS 27 Home zoom is pivoted around the tapped app icon while the transition runs.

The existing Hoisly `MainActivity` launch sequencing and `LauncherViewModel` transition lifecycle are retained. This avoids importing Launchii's unrelated launcher architecture, app repository, wallpaper service, folders, widgets, drag system, settings, or other UI features.

The existing notification badge work from the base ZIP is retained, including the notification listener manifest entry and bold badge font.

## Build status

The source was updated and reviewed, but a Gradle compile could not be completed in this environment because the Gradle 9.8.1 distribution could not be downloaded (`services.gradle.org` DNS/network unavailable). Please run the repository's GitHub Actions build to verify compilation on CI before publishing an APK.
