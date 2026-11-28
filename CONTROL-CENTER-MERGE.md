# Control Center merge (OnePlus Control iOS redesign -> Hoisly)

Source: onepluscontrolcenter-ios-redesign, moved into `app/src/main/java/com/hoisly/launcher/controlcenter/`
(package `digital.tripolskiy.opshade` -> `com.hoisly.launcher.controlcenter`). Same app module and applicationId.

Added: ShadeService (accessibility overlay), NotifListener, ControlCenterSetupActivity (was SetupActivity),
TileEditActivity, ReplyActivity; res/xml/cc_accessibility_service.xml; res/values*/controlcenter_strings.xml;
permissions ACCESS_FINE_LOCATION and MODIFY_AUDIO_SETTINGS; Shizuku keep rule; Settings > "Control Center" tile.
Reused from Hoisly: Shizuku provider (FaceShizukuProvider), Shizuku/Palette dependencies, INTERNET etc.
Removed: TestNotifications/TestReceiver (sample-notification tooling). NotifListener uses BundleCompat (minSdk 31).

Setup: open Hoisly Settings > Control Center, enable the accessibility service and notification access.
Optional: adb shell pm grant com.hoisly.launcher android.permission.WRITE_SECURE_SETTINGS

NOT BUILD-VERIFIED (no Android SDK here). Run ./gradlew :app:assembleDebug :app:testDebugUnitTest.
Known risks: both IslandService (status bar overlay) and ShadeService catch the top-edge swipe - check for
touch overlap on device; Compose BOM moved from 2025.05 to 2026.09 for the imported code; the imported UI
still has hard-coded English strings (not yet moved into strings.xml / translations); Notifications.kt is 473 lines.

## One-tap setup
Settings > Control Center > "Enable everything" (AutoSetup.kt + ControlCenterSetupActivity).
- Shizuku allowed: grants WRITE_SECURE_SETTINGS, runtime permissions, WRITE_SETTINGS, DND access, notification listener and
  appends the accessibility service (keeps Dynamic Island enabled) - no Settings screens at all.
- No Shizuku: guided flow opens each missing system screen in turn and returns automatically; stops if a screen is backed out of.
Android still requires the user to enable accessibility/notification access by hand when sideloaded apps are "restricted"
(Settings > App info > menu > Allow restricted settings).
