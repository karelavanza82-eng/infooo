# Hoisly: parallel visual launch bursts and automatic landscape notch

This source update changes the launcher transition to independent, non-interactive visual popup bursts.

- Every app-icon tap creates its own short visual burst; earlier bursts are not cancelled by later taps.
- The bursts are UI-only and do not create extra Android tasks or launch multiple apps in parallel.
- The launcher waits for a short 430 ms tap-settle interval, then launches only the last selected app. This keeps the launcher responsive during rapid taps and avoids stacking app tasks.
- The existing return-to-home zoom remains intact.
- Dynamic Island landscape settings now include a fourth `Follow the notch` choice and migrate existing settings to it by default.
- `Follow the notch` uses the current display cutout bounds where Android exposes them, with display rotation as fallback.
- The custom Hoisly status-bar overlay is hidden automatically in landscape and shown again in portrait. Native SystemUI status icons can only be hidden when Android grants `WRITE_SECURE_SETTINGS`; otherwise the OS may still show its own status-bar area/icons.

## Build status

The source was updated, but a Release APK could not be compiled in this environment: Gradle 9.8.1 could not be downloaded because `services.gradle.org` was unreachable, and no local Android SDK/Gradle distribution was available. Run the repository's GitHub Actions workflow to compile and verify the APK before installing it.
