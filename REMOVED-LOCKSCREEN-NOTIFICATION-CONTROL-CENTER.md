# Removed: Lock Screen, Notification Center, Control Center

Deleted: panels/{PanelController,PanelHost,PanelEdge,PanelDrag}.kt, panels/notifications/*,
panels/control/{ControlCenterPanel,ControlLogic,ControlActions,Torch}.kt, ui/lockscreen/*,
ui/settings/{PanelsSettingsScreen,LockScreenSettingsScreen}.kt, settings/PanelPreferences.kt,
and their unit tests.

Kept: panels/control/NowPlaying.kt (used by the Music widget).

Edited: MainActivity, HomeScreen, HomeCommands, HomeCommandDispatcher, HomeBehavior (the stored
"notifications" gesture now decodes to None), HomeBehaviorScreen, SettingsApp, SettingsState,
LauncherSettings, IslandListenerService (badges and Island untouched).

Not build-verified (no Android SDK in the authoring environment): run ./gradlew assembleDebug testDebugUnitTest.
Unused string resources were left in place so the translation consistency test keeps passing.
