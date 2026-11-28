# Hoisly Control Center repair progress

This patch continues from `Hoisly-StatusBar-Island-Full-Fix.zip`.

## Implemented in this source patch
- Landscape shade now has two gesture stages: the first pull is capped at stage 1 (status bar only); a new pull that starts from stage 1 can expand to stage 2 (Control Center).
- The custom status bar remains visible while the landscape shade is in either open stage and hides again after the shade closes.
- Landscape background dimming and Control Center content are delayed until stage 2, so the first pull does not reveal the Control Center.
- The shared Liquid Glass renderer now uses the interactive Button lens profile for glass buttons, SmallButton for the AirPlay control, Button for transport controls, and Tile for the album-art placeholder. This makes these controls use the same lens/highlight pipeline rather than the non-interactive Panel profile.
- Previous landscape sizing patch remains: layout scales from the short screen edge and the 568x360 reference canvas.
- Duplicate carrier/battery status labels remain removed from the Control Center status row.

## Still not implemented / requires additional engineering
- Arbitrary drag-and-drop positioning of every control and independent width/height editing for every control.
- Runtime discovery/import of OEM SystemUI quick-setting tiles. Android does not expose a public API for third-party apps to enumerate and embed all vendor QS tiles; supported Android actions and app-owned alternatives must be used.
- Full device-matrix verification and release build.

## Build verification
Not verified in this environment: Gradle wrapper could not download Gradle 9.8.1 because `services.gradle.org` was unreachable (`UnknownHostException`). Run `./gradlew :app:compileDebugKotlin` and tests in GitHub Actions with network access before installing.
