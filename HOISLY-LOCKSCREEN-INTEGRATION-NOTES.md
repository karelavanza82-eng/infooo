# Hoisly Lock Screen integration — first implementation

## Included
- `HoislyLockScreenActivity`: Compose-based KLCK-inspired lock surface.
- Preset background bitmap copied from the supplied `iOS_26_beta2.klck.zip` (`IMG404489b02bb7bdef1f4a8823ac927ca3`, 1080x2400 WebP).
- SF UI Regular, Medium and Bold font files copied from the supplied preset.
- Live system clock and Indonesian date.
- Swipe-up calls `KeyguardManager.requestDismissKeyguard`; Hoisly never checks or bypasses PIN/pattern/biometric credentials.
- Camera shortcut launches Android's camera intent.
- Torch shortcut requests CAMERA permission and toggles the first camera flash using `CameraManager`.
- Settings route and Lock Screen tile open a preview Activity.

## Security and platform limitation
This is an app-owned lock-screen surface that can be shown over the system keyguard when launched. It does **not** replace Android's privileged Keyguard implementation. Android continues to own authentication. A normal third-party launcher cannot reliably auto-launch an Activity on every screen wake on modern Android due background-activity-launch restrictions; this first patch exposes a manual preview entry from Hoisly Settings. Automatic wake presentation requires a supported device/OEM privileged integration or another permitted system mechanism and is not claimed here.

## Preset fidelity limitation
The supplied KLCK preset metadata reports `locked: true`. Its exported JSON contains global settings and asset references, but not the full editable layer tree. Therefore this patch reconstructs the visible design from the supplied portrait thumbnail and the original wallpaper/font assets; it is not a lossless one-to-one conversion of every KLCK layer, animation, weather widget, notification counter, or music widget. Those live data sources and remaining visuals are follow-up work.

## Verification
- AndroidManifest XML parsed successfully.
- Referenced drawable/font assets exist.
- Gradle signing configuration adjusted so `assembleDebug` does not require release keystore secrets; release signing remains enabled when all four `HOISLY_*` values are supplied.
- Gradle build was attempted with `./gradlew :app:assembleDebug --no-daemon`, but could not start because this environment has no Android SDK and cannot resolve/download Gradle 9.8.1 (`UnknownHostException: services.gradle.org`). This ZIP is source-modified, **not build-verified**.

## Build on an Android/CI environment
Run:

```bash
./gradlew :app:assembleDebug testDebugUnitTest
```
