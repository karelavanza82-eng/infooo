package com.hoisly.launcher.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoisly.launcher.R
import com.hoisly.launcher.ui.settings.IconAppearanceRows
import com.hoisly.launcher.ui.settings.IconPreviewRow
import com.hoisly.launcher.ui.settings.SectionFooter
import com.hoisly.launcher.ui.settings.SettingsCard
import com.hoisly.launcher.ui.settings.SettingsViewModel
import com.hoisly.launcher.ui.settings.sampleApps

/** Apps in the live icon preview of the Appearance step. */
private const val PREVIEW_APPS = 4

/**
 * Asks for the home role. The flow moves on by itself once Hoisly is the home app (checked when
 * the activity resumes); after a refusal it stays here and can be skipped.
 */
@Composable
internal fun DefaultHomeStep(progress: StepProgress?, onRequest: () -> Unit, onSkip: () -> Unit) {
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_home_title),
        subtitle = stringResource(R.string.onboarding_home_body),
        primaryText = stringResource(R.string.onboarding_home_action),
        onPrimary = onRequest,
        secondaryText = stringResource(R.string.onboarding_not_now),
        onSecondary = onSkip,
        header = { StepGlyph(Icons.Rounded.Home) },
    )
}

/** Opens the system "All files access" screen; without it the glass keeps its flat fallback. */
@Composable
internal fun WallpaperAccessStep(progress: StepProgress?, onRequest: () -> Unit, onSkip: () -> Unit) {
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_wallpaper_title),
        subtitle = stringResource(R.string.onboarding_wallpaper_body),
        primaryText = stringResource(R.string.onboarding_wallpaper_action),
        onPrimary = onRequest,
        secondaryText = stringResource(R.string.onboarding_not_now),
        onSecondary = onSkip,
        header = { StepGlyph(Icons.Rounded.BlurOn) },
    )
}

/**
 * Last step: icon look with a live row of the user's own icons. The choice is saved on tap (the
 * same setting as Settings › Icons), so Get Started is the only button.
 */
@Composable
internal fun AppearanceStep(vm: SettingsViewModel, progress: StepProgress?, onNext: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps = sampleApps(vm, PREVIEW_APPS)
    StepScaffold(
        progress = progress,
        title = stringResource(R.string.onboarding_appearance_title),
        subtitle = stringResource(R.string.onboarding_appearance_body),
        primaryText = stringResource(R.string.onboarding_get_started),
        onPrimary = onNext,
        header = { SettingsCard { IconPreviewRow(apps, settings) } },
    ) {
        SettingsCard {
            IconAppearanceRows(settings.iconAppearance) { appearance ->
                vm.settings.update { it.copy(iconAppearance = appearance) }
            }
        }
    }
}
