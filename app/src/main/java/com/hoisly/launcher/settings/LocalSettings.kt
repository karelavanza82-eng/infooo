package com.hoisly.launcher.settings

import androidx.compose.runtime.compositionLocalOf

/** Current settings for composables that render launcher content (icons, labels, pages). */
val LocalSettingsState = compositionLocalOf { SettingsState() }
