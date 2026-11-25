# Changes in this build (not compiled by the author; run CI first)

## Control Center (new)
- Drag down from the top-right 40% of the status-bar edge. Left part still opens the Notification Center.
- Real controls: flashlight (CameraManager), volume (AudioManager), brightness and rotation lock
  (need "Modify system settings"), Focus (Do Not Disturb, asks for policy access), calculator, camera,
  media controls (existing NowPlaying).
- Wi-Fi, Mobile Data, Bluetooth, Airplane Mode: Android does not let apps switch them, so the tiles
  show state where readable and open Android's own panel/settings page.
- Switch in Settings > Control & Notifications. New permissions: ACCESS_WIFI_STATE, WRITE_SETTINGS.

## Lock Screen (rebuilt)
- Clock weight/size/position, minute-aligned clock, battery pill, wallpaper backdrop with blur,
  notification cards (needs Notification Center), working flashlight and secure-camera buttons,
  finger-following swipe-up with spring and fling. Settings > Lock Screen.
- Still a decorative layer above Android's keyguard; security stays with Android.

## Backup and Restore (new)
- Settings > Backup and Restore: one JSON file (settings + layout.json) via the system file picker.
- Restore replaces settings and layout and restarts the process. Custom icons/widget photos not included.

## Tests added
ControlLogicTest, LockLogicTest, BackupCodecTest, PanelPreferences lock-screen tests.
