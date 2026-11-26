# Hoisly Android 9–12 Liquid Glass compatibility

This package is a compatibility fork of the supplied Hoisly source archive.

## What changed
- Android 13+ continues to use the original AGSL `RuntimeShader` implementation.
- Android 9–12 now route Liquid mode to `LegacyLiquidGlassPainter`, which uses Android Canvas
  bitmap, linear-gradient and radial-gradient shaders. It consumes the same wallpaper body,
  adaptive luminance tone, light tilt, profile rim sizing, touch state and LiquidGlassTokens.
- The Liquid Glass body bitmap is now prepared for Android 9–12 as well.
- Android 9 uses the classic Home settings route instead of Android 10's `RoleManager`.
- Wallpaper access and display bounds are guarded for APIs introduced in Android 10.

## Important visual limitation
The legacy renderer preserves the Hoisly Liquid Glass visual system and its tuning, but Android
9–12 do not provide AGSL. Canvas gradients cannot perform the same arbitrary per-pixel lens
refraction/chromatic aberration as the original AGSL shader. Exact pixel parity is therefore
not guaranteed; final tuning requires screenshots and performance checks on real Android 9–12
devices. The original API 33+ shader source is retained unchanged.

## Build / verification status
This source archive has not been compiled in this environment. A successful Gradle build and
device-level tests are required before producing a release APK. No claim of a verified APK is made.
