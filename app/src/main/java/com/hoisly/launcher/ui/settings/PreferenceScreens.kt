package com.hoisly.launcher.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.R
import com.hoisly.launcher.data.AppInfo
import com.hoisly.launcher.data.AppEntry
import com.hoisly.launcher.settings.LocalSettingsState
import com.hoisly.launcher.settings.PageTransition
import com.hoisly.launcher.settings.SettingsState
import com.hoisly.launcher.ui.home.pageTransition
import com.hoisly.launcher.ui.home.HomePreview
import com.hoisly.launcher.ui.home.drawAppTransitionVisual
import com.hoisly.launcher.ui.icon.AppIconImage
import com.hoisly.launcher.ui.icon.IconLabel
import com.hoisly.launcher.ui.shape.ContinuousRoundedShape
import com.hoisly.launcher.ui.shape.shapeBorder
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// region General

/** Navigation bar, glass effect and icon label size. */
@Composable
fun GeneralSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val selfPackage = LocalContext.current.packageName
    val sample = apps.firstOrNull { it.packageName != selfPackage }
    // Local value while dragging; persisted when the finger is lifted.
    var scale by remember(settings.textScale) { mutableFloatStateOf(settings.textScale) }
    var labelSpacing by remember(settings.labelSpacing) { mutableFloatStateOf(settings.labelSpacing) }

    SettingsPage(title = stringResource(R.string.row_general), onBack = onBack) {
        item {
            SettingsCard {
                SwitchRow(stringResource(R.string.general_blur), settings.blurEffect, vm.settings::setBlurEffect)
                SwitchRow(stringResource(R.string.general_custom_text_size), settings.customTextSize, vm.settings::setCustomTextSize)

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (sample != null) {
                        val previewScale = if (settings.customTextSize) scale else 1f
                        CompositionLocalProvider(
                            LocalSettingsState provides settings.copy(customTextSize = true, textScale = previewScale),
                        ) {
                            AppIconImage(sample, size = 64.dp)
                            Spacer(Modifier.height(labelSpacing.dp))
                            IconLabel(sample.label)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    IosSlider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = SettingsState.MIN_TEXT_SCALE..SettingsState.MAX_TEXT_SCALE,
                        enabled = settings.customTextSize,
                        onValueChangeFinished = { vm.settings.setTextScale(scale) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(scale * 100).roundToInt()}%", color = SettingsStyle.SecondaryText, fontSize = 14.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("Icon label spacing", color = Color.White, fontSize = 14.sp)
                    IosSlider(
                        value = labelSpacing,
                        onValueChange = { labelSpacing = it },
                        valueRange = SettingsState.MIN_LABEL_SPACING..SettingsState.MAX_LABEL_SPACING,
                        onValueChangeFinished = { vm.settings.update { it.copy(labelSpacing = labelSpacing) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${labelSpacing.roundToInt()} dp", color = SettingsStyle.SecondaryText, fontSize = 14.sp)
                }
            }
        }
        if (settings.blurEffect && !actions.hasWallpaperAccess) {
            item { SectionFooter(stringResource(R.string.general_wallpaper_access_body)) }
            item {
                Spacer(Modifier.height(12.dp))
                SettingsCard {
                    NavRow(Icons.Rounded.Wallpaper, stringResource(R.string.general_wallpaper_access), onClick = actions::requestWallpaperAccess)
                }
            }
        }
    }
}

// endregion

// region Transition effect

/** Page swipe effect, with an animated preview that replays on every change. */
@Composable
fun TransitionSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()

    SettingsPage(title = stringResource(R.string.row_transition), onBack = onBack) {
        item { TransitionPreview(settings.pageTransition, apps) }
        item { SectionHeader(stringResource(R.string.transition_section)) }
        item {
            SettingsCard {
                PageTransition.entries.forEach { effect ->
                    CheckRow(stringResource(effect.labelRes), effect == settings.pageTransition) {
                        vm.settings.setPageTransition(effect)
                    }
                }
            }
        }
    }
}

/** Three mini home screen pages that swipe to the next page and back using [effect]. */
@Composable
private fun TransitionPreview(effect: PageTransition, apps: List<AppInfo>) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(effect) {
        progress.snapTo(0f)
        delay(250)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(700)
        progress.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
    }
    val pageWidth = 128.dp
    val pageWidthPx = with(LocalDensity.current) { pageWidth.toPx() }

    SettingsCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            for (page in -1..2) {
                MiniPage(
                    apps = apps.drop((page + 1) * MINI_PAGE_ICONS).take(MINI_PAGE_ICONS),
                    modifier = Modifier
                        .offset { IntOffset(((page - progress.value) * pageWidthPx).roundToInt(), 0) }
                        .pageTransition(effect) { page - progress.value }
                        .size(pageWidth - 12.dp, 210.dp),
                )
            }
        }
    }
}

private const val MINI_PAGE_ICONS = 16

@Composable
private fun MiniPage(apps: List<AppInfo>, modifier: Modifier = Modifier) {
    val shape = ContinuousRoundedShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .shapeBorder(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { AppIconImage(it, size = 20.dp) }
            }
        }
    }
}

// endregion

// region App animations

@Composable
fun AnimationSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val apps by vm.repository.apps.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val selfPackage = LocalContext.current.packageName
    val previewState = remember(apps, settings) { vm.homePreview(apps, settings) }
    val previewApp = previewState.dock.filterIsInstance<AppEntry>().firstOrNull()?.app
        ?: apps.firstOrNull { it.packageName != selfPackage }

    SettingsPage(title = stringResource(R.string.row_animations), onBack = onBack) {
        item {
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.animations_resume),
                    description = stringResource(R.string.animations_resume_description),
                    checked = settings.resumeAnimation,
                    onCheckedChange = vm.settings::setResumeAnimation,
                )
                SwitchRow(
                    title = stringResource(R.string.animations_open_close),
                    description = stringResource(R.string.animations_open_close_description),
                    checked = settings.openCloseAnimation,
                    onCheckedChange = vm.settings::setOpenCloseAnimation,
                )
                var speedDraft by remember(settings.animationSpeedPercent) {
                    mutableFloatStateOf(settings.animationSpeedPercent.toFloat())
                }
                SliderRow(
                    title = "Animation speed",
                    value = speedDraft,
                    valueText = "${speedDraft.toInt()}%",
                    onValueChange = { speedDraft = it },
                    onValueChangeFinished = { vm.settings.setAnimationSpeedPercent(speedDraft.toInt()) },
                    onValueChangeCancelled = { speedDraft = settings.animationSpeedPercent.toFloat() },
                    valueRange = 0f..100f,
                )
            }
        }
        item {
            Text(
                text = "Contoh animasi",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            Text(
                text = "Pratinjau bergerak memakai wallpaper dan susunan ikon Home Hoisly kamu.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val previewName = when (settings.appAnimationStyle) {
                "IOS27" -> "iOS 27"
                "AURA" -> "Aura"
                "SHUTTER" -> "Shutter"
                "ORBIT" -> "Orbit"
                "ANTICIPATE" -> "Anticipate"
                "SCAN" -> "Scan"
                "MIST" -> "Mist"
                "PARALLEL" -> "Parallel"
                else -> "Default Hoisly"
            }
            Text(
                text = "Preview: $previewName",
                fontSize = 14.sp,
                color = Color(0xFF8BC1FF),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            AppAnimationStylePreview(
                style = settings.appAnimationStyle,
                state = previewState,
                wallpaper = wallpaper,
                prefs = settings,
                sampleApp = previewApp,
            )
        }
        item {
            Text(
                text = "Animation style",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            SettingsCard {
                val styles = listOf(
                    "HOISLY" to ("Default Hoisly" to "Animasi bawaan Hoisly seperti sebelumnya"),
                    "IOS27" to ("iOS 27" to "Zoom spring dari ikon ke layar, lalu kembali ke ikon"),
                    "AURA" to ("Aura" to "Cahaya warna aplikasi tumbuh dari ikon, wallpaper meredup"),
                    "SHUTTER" to ("Shutter" to "Lapisan horizontal terbuka bertahap dari posisi ikon"),
                    "ORBIT" to ("Orbit" to "Enam cahaya berputar mengelilingi titik asal ikon"),
                    "ANTICIPATE" to ("Anticipate" to "Layar mundur sedikit lalu melesat ke depan"),
                    "SCAN" to ("Scan" to "Dua garis cahaya membelah layar dengan efek frosted"),
                    "MIST" to ("Mist" to "Kabut cahaya lembut bergerak dari tepi layar"),
                    "PARALLEL" to ("Parallel" to "Ketuk banyak ikon cepat: tiap ikon membuka kartunya sendiri bersamaan, app terakhir yang terbuka"),
                )
                styles.forEach { (style, details) ->
                    val selected = settings.appAnimationStyle == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.045f))
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) Color(0xFF55A7FF) else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(18.dp),
                            )
                            .clickable { vm.settings.setAppAnimationStyle(style) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(details.first, fontSize = 15.sp, color = Color.White)
                            Text(details.second, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f))
                        }
                        if (selected) Text("✓", color = Color(0xFF55A7FF), fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

/**
 * An animated, phone-shaped preview built from Hoisly's own HomePreview. It uses the user's
 * saved Hoisly layout, installed app icons and the wallpaper loaded by SettingsViewModel;
 * no Launchii screenshot, wallpaper, icon grid or launcher UI is bundled into this preview.
 */
@Composable
private fun AppAnimationStylePreview(
    style: String,
    state: com.hoisly.launcher.data.HomeState,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    prefs: SettingsState,
    sampleApp: AppInfo?,
) {
    val progress = remember { Animatable(0f) }
    val previewColor = remember(sampleApp?.packageName) {
        when (sampleApp?.packageName?.hashCode()?.and(3)) {
            0 -> Color(0xFF2878E8)
            1 -> Color(0xFF9C4DCE)
            2 -> Color(0xFF19A77A)
            else -> Color(0xFFE46A42)
        }
    }
    LaunchedEffect(style, prefs.animationSpeedPercent) {
        val speedFactor = (1.35f - prefs.animationSpeedPercent.coerceIn(0, 100) / 100f * 0.7f).coerceIn(0.55f, 1.35f)
        val duration = when (style) {
            "IOS27" -> 650
            "AURA" -> 520
            "SHUTTER" -> 500
            "ORBIT" -> 620
            "ANTICIPATE" -> 480
            "SCAN" -> 520
            "MIST" -> 580
            "PARALLEL" -> 900
            else -> 420
        }
        val easing = when (style) {
            "IOS27", "ANTICIPATE" -> com.hoisly.launcher.ui.home.LaunchiiAppMotion.spring((duration * speedFactor).toLong())
            "SHUTTER" -> androidx.compose.animation.core.LinearOutSlowInEasing
            "ORBIT" -> androidx.compose.animation.core.FastOutSlowInEasing
            "AURA", "SCAN", "MIST" -> androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
            else -> FastOutSlowInEasing
        }
        while (true) {
            progress.snapTo(0f)
            delay(350)
            progress.animateTo(1f, tween((duration * speedFactor).roundToInt().coerceAtLeast(1), easing = easing))
            delay(420)
            progress.animateTo(0f, tween((duration * 0.82f * speedFactor).roundToInt().coerceAtLeast(1), easing = easing))
            delay(550)
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(196.dp)
                .clip(ContinuousRoundedShape(30.dp))
                .border(1.dp, Color.White.copy(alpha = 0.22f), ContinuousRoundedShape(30.dp)),
        ) {
            HomePreview(
                state = state,
                wallpaper = wallpaper,
                modifier = Modifier.fillMaxWidth(),
                prefs = prefs,
            )
            Canvas(Modifier.matchParentSize()) {
                val iconW = size.width * 0.17f
                val iconH = iconW
                val dockIndex = state.dock.indexOfFirst { it is AppEntry }.coerceAtLeast(0)
                val dockSlots = prefs.dockSlots.coerceAtLeast(1)
                val iconCenterX = size.width * ((dockIndex + 0.5f) / dockSlots).coerceIn(0.05f, 0.95f)
                val iconCenterY = size.height * 0.91f
                val iconLeft = iconCenterX - iconW / 2f
                val iconTop = iconCenterY - iconH / 2f
                val bounds = Rect(iconLeft, iconTop, iconLeft + iconW, iconTop + iconH)
                drawAppTransitionVisual(style, bounds, null, previewColor, progress.value)
            }
            if (progress.value > 0.78f) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = ((progress.value - 0.78f) / 0.22f).coerceIn(0f, 1f) },
                ) {
                    if (sampleApp != null) AppIconImage(sampleApp, size = 48.dp, badge = com.hoisly.launcher.ui.icon.IconBadge.None)
                }
            }
        }
    }
}

// endregion
