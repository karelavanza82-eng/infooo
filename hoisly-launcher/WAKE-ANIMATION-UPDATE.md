# Wake / unlock animation update

Updated `app/src/main/java/com/hoisly/launcher/ui/home/WakeAnimation.kt`.

Changes:
- Removed the full-screen alpha-to-zero behavior that could expose a blank frame between unlock and Home.
- Kept the Home surface rendered behind the system lock screen, with a restrained 1.10x camera push.
- Added a per-item pluffy spring entrance for app icons and widgets, including a small lift and scale settle.
- Staggered items by their existing grid seed (11 ms per step, capped at 40 steps) so they do not all appear together.
- Used the existing gel spring for the Bounce style; other wake styles retain their respective movement.

Build status:
- Source changes are packaged.
- `:app:assembleDebug` could not be run in this environment because Gradle 9.8.1 could not be downloaded (network DNS unavailable). Open the project in Android Studio and let Gradle sync before building.

Platform limitation:
- The launcher cannot replace TECNO/HiOS's system keyguard unlock animation. This patch controls the launcher's Home surface and its contents as the lock screen reveals it.
