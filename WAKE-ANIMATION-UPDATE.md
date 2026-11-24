# Wake / unlock animation

Removed. After unlock the home screen is shown as it is, with no entrance animation, no wake
broadcast receiver and no "Wake animation" setting. The system keyguard animation is unchanged.
Also fixed: `StateFlow.value` read in composition in `ui/widgets/MusicWidget.kt` (lint error).
