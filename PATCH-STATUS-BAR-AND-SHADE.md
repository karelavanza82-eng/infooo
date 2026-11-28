# Hoisly patch notes (completed source changes; build unverified)

This patch is intentionally limited to low-risk behavior changes in the supplied source:

1. The custom status bar is no longer suppressed solely because the keyguard/lock screen is showing. It still respects screen-off and the custom shade being open.
2. When `hideInLandscape` is enabled, the custom status bar remains hidden at rest and is temporarily revealed while the user starts a pull-down gesture in landscape. The existing system-icon hiding behavior remains in place to avoid duplicate icons.
3. The replacement shade now also dismisses SystemUI's native notification shade when the pull finishes with `ACTION_UP`, not only when SystemUI cancels the gesture. This addresses the common double-shade path.

## Additional requested work now included

- A rightward swipe from Control Center opens a notification page with the existing notification cards, including open, dismiss, clear-all, action/reply, snooze and per-app settings.
- Control Center glass surfaces now use Hoisly's shared `com.hoisly.launcher.ui.glass` renderer and request Liquid mode with the wallpaper source when available.
- A settings page exposes overall size, horizontal/vertical position and background transparency, with saved values and reset.
- Dynamic Island touch forwarding now translates events using actual screen origins for both overlay windows, rather than estimating the touch-window origin from raw pointer coordinates.

## Build status

The Gradle wrapper could not download Gradle 9.8.1 in this environment because the Gradle distribution host was unreachable. No successful compile or APK build is claimed. Please run the project's normal Gradle build in an environment with the wrapper distribution and Android dependencies available before installing.
