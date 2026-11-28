# Hoisly patch 2 — requested status bar, Control Center and Dynamic Island work

## Changes included

1. **Lock-screen status bar**
   - The custom Hoisly status bar is no longer hidden solely because Android is on the keyguard.
   - It remains available while the screen is on, including the lock screen.

2. **Landscape status bar gesture**
   - With `hideInLandscape` enabled, the custom bar stays hidden at rest.
   - The landscape pull gesture temporarily reveals it; the native icons remain subject to the existing secure-setting permission.
   - The in-panel duplicate status row is suppressed in landscape.
   - The temporary reveal is also handled when SystemUI cancels/pilfers the original touch stream.

3. **Control Center takeover**
   - The existing SystemUI shade-dismiss takeover now runs on normal gesture release as well as cancellation, reducing the chance of the OEM shade remaining underneath and appearing double.
   - The repeated dismiss window is retained to handle SystemUI reopening the shade while the pull gesture is finishing.

4. **Notification page**
   - A rightward swipe from the Control Center page opens a separate notification page.
   - Notification open, dismiss, clear-all, action/reply, snooze and per-app notification settings are connected to the existing notification listener/service.

5. **Shared Hoisly Liquid Glass**
   - Control Center glass surfaces now call the existing Hoisly glass renderer instead of the local Control Center-only gradient/rim implementation.
   - The overlay loads the same wallpaper glass source and requests Hoisly's Liquid mode. If wallpaper access is unavailable, the renderer uses its built-in fallback.

6. **User layout controls**
   - Added a settings page in the Control Center rail.
   - Users can adjust overall size, horizontal position, vertical position and background transparency.
   - Values are persisted and include a reset option.

7. **Dynamic Island tap forwarding**
   - The invisible touch window now translates events using the actual screen origins of both windows instead of deriving the origin from the first pointer's raw/local coordinates.
   - Forwarded events are recycled safely and the island hit window retains ownership of its taps.

## Important validation note

The source archive was inspected and the modified Kotlin files passed a syntax-oriented parser check (no Kotlin parse errors were reported). A full Android Gradle compile could not be completed in this environment because the Gradle wrapper distribution host (`services.gradle.org`) could not be resolved, and no Android SDK is installed in the build container. Therefore this is a source patch, **not a verified release APK**. Build and device-test before installing over the current app.

## Device checks recommended

- Lock the phone and confirm the Hoisly bar remains visible.
- Rotate to landscape, wait until the custom bar hides, then pull down; confirm it appears only during the pull and does not duplicate the OEM bar.
- Pull down the Control Center and confirm the OEM shade disappears.
- Swipe right on the Control Center to open notifications; swipe notification cards and test their actions.
- Open the gear page, change size/position/transparency, close and reopen the panel; confirm values persist.
- Tap the Dynamic Island music capsule and test play/pause/open-app, then test expanded mode and bubble taps.
