# Hoisly charging and status-bar indicator update

## Charging island
- The compact charging card now shows the battery percentage inside a green rounded battery body.
- A small battery terminal nub follows the body and a white lightning bolt sits immediately to its right, matching the supplied iOS reference.
- The generic island activity dot is suppressed while the charging card is active; charging no longer displays a separate green/orange dot.

## System status bar
- Added a `doNotDisturb` state sourced from Android Zen Mode (`Settings.Global` `zen_mode`).
- The status bar listens for `NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED` and refreshes the crescent indicator as DND is enabled or disabled.
- The crescent is drawn immediately to the left of the clock. Existing silent-bell, airplane, network, and battery indicators remain intact.

## Scope and verification
- Changes are limited to charging presentation and status-bar state/drawing.
- No notification listener, accessibility service, permission flow, settings persistence, or event-routing behavior was intentionally changed.
- Gradle compilation could not be run in this environment because the Gradle 9.8.1 wrapper distribution could not be downloaded (`UnknownHostException` for `services.gradle.org`). The archive is source-only and has not been certified as a successful build.
