package com.hoisly.launcher.ui.settings

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.R
import com.hoisly.launcher.settings.PageTransition
import com.hoisly.launcher.ui.glass.GlassProfile
import com.hoisly.launcher.ui.glass.glass
import com.hoisly.launcher.ui.onboarding.AppLogo
import com.hoisly.launcher.ui.onboarding.finishSetupItems
import com.hoisly.launcher.ui.onboarding.setupState
import com.hoisly.launcher.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun pick(indo: Boolean, id: String, en: String) = if (indo) id else en

/**
 * Settings home as an animated "bento" board instead of one long list: a living hero card,
 * big gradient tiles in a grid, a horizontal carousel for appearance, and compact info tiles.
 * Every tile springs in with a stagger and squashes when pressed.
 */
@Composable
fun MainSettingsScreen(vm: SettingsViewModel, actions: SettingsActions, navigate: (SettingsRoute) -> Unit) {
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val indo = locale.language == "in" || locale.language == "id"
    val unfinished = finishSetupItems(actions.setupState()).size
    val T = SettingsStyle.Tile

    SettingsPage(title = stringResource(R.string.settings_title), onBack = null) {
        item { HeroCard(actions.isDefaultHome, onClick = actions::requestDefaultHome) }

        if (unfinished > 0) {
            item { Spacer(Modifier.height(14.dp)) }
            item { FinishSetupBanner(unfinished) { navigate(SettingsRoute.FinishSetup) } }
        }

        // ── Main ─────────────────────────────────────────────
        item { BentoHeader(pick(indo, "Utama", "Main"), 1) }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                BentoTile(
                    icon = Icons.Rounded.ViewCarousel,
                    title = "Dynamic Island",
                    subtitle = pick(indo, "Aktivitas langsung & kapsul", "Live activities & capsule"),
                    color = T.Purple,
                    kind = TileKind.Wide,
                    pulse = true,
                    index = 2,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { context.startActivity(Intent().setClassName(context, "com.akira.island.ui.MainActivity")) },
                )
            }
        }
        item {
            BentoRow {
                BentoTile(Icons.Rounded.Settings, stringResource(R.string.row_general), T.Gray, { navigate(SettingsRoute.General) }, Modifier.weight(1f), index = 3)
                BentoTile(Icons.Rounded.SignalCellularAlt, stringResource(R.string.statusbar_title), T.Blue, { navigate(SettingsRoute.StatusBar) }, Modifier.weight(1f), index = 4)
            }
        }
        item {
            BentoRow {
                BentoTile(Icons.Rounded.Backup, stringResource(R.string.backup_title), T.Green, { navigate(SettingsRoute.Backup) }, Modifier.weight(1f), index = 5)
                BentoTile(
                    Icons.Rounded.Translate, stringResource(R.string.row_language), T.Teal, { navigate(SettingsRoute.Language) }, Modifier.weight(1f), index = 6,
                    subtitle = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) },
                )
            }
        }
        item {
            BentoRow {
                BentoTile(
                    Icons.Rounded.GridView, stringResource(R.string.row_grid), T.Indigo, { navigate(SettingsRoute.Grid) }, Modifier.weight(1f), index = 7,
                    subtitle = (layout?.grid ?: vm.savedGrid()).toString(),
                )
                BentoTile(Icons.Rounded.WbSunny, stringResource(R.string.widget_weather), T.Cyan, { navigate(SettingsRoute.Weather) }, Modifier.weight(1f), index = 8)
            }
        }

        // ── Appearance carousel ──────────────────────────────
        item { BentoHeader(pick(indo, "Tampilan", "Appearance"), 9) }
        item {
            LazyRow(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    BentoTile(
                        Icons.Rounded.Palette, stringResource(R.string.row_dynamic_color),
                        if (settings.dynamicColor) T.Pink else T.Gray,
                        { vm.settings.setDynamicColor(!settings.dynamicColor) },
                        Modifier.width(150.dp), index = 10,
                        subtitle = if (settings.dynamicColor) pick(indo, "Aktif", "On") else pick(indo, "Mati", "Off"),
                    )
                }
                item { BentoTile(Icons.Rounded.BlurOn, stringResource(R.string.row_glass), T.Teal, { navigate(SettingsRoute.Glass) }, Modifier.width(150.dp), index = 11) }
                item { BentoTile(Icons.Rounded.Apps, stringResource(R.string.row_icons), T.Orange, { navigate(SettingsRoute.Icons) }, Modifier.width(150.dp), index = 12) }
                item { BentoTile(Icons.Rounded.Folder, stringResource(R.string.area_folders), T.Indigo, { navigate(SettingsRoute.Folders) }, Modifier.width(150.dp), index = 13) }
                item { BentoTile(Icons.Rounded.Dock, stringResource(R.string.row_home_screen), T.Purple, { navigate(SettingsRoute.HomeScreen) }, Modifier.width(150.dp), index = 14) }
                item { BentoTile(Icons.Rounded.ViewModule, stringResource(R.string.library_title), T.Blue, { navigate(SettingsRoute.AppLibrary) }, Modifier.width(150.dp), index = 15) }
                item { BentoTile(Icons.Rounded.Apps, stringResource(R.string.gestures_home), T.Green, { navigate(SettingsRoute.HomeBehavior) }, Modifier.width(150.dp), index = 16) }
                item { BentoTile(Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.row_context_menu), T.Gray, { navigate(SettingsRoute.ContextMenu) }, Modifier.width(150.dp), index = 17) }
            }
        }

        // ── Motion ───────────────────────────────────────────
        item { BentoHeader(pick(indo, "Gerakan", "Motion"), 18) }
        item {
            BentoRow {
                BentoTile(
                    Icons.Rounded.ViewCarousel, stringResource(R.string.row_transition), T.Red, { navigate(SettingsRoute.Transition) }, Modifier.weight(1f), index = 19,
                    subtitle = stringResource(settings.pageTransition.labelRes),
                )
                BentoTile(Icons.Rounded.Animation, stringResource(R.string.row_animations), T.Yellow, { navigate(SettingsRoute.Animations) }, Modifier.weight(1f), index = 20, pulse = true)
            }
        }

        // ── Personal ─────────────────────────────────────────
        item { BentoHeader(pick(indo, "Personal", "Personal"), 21) }
        item {
            BentoRow {
                BentoTile(
                    Icons.Rounded.VisibilityOff, stringResource(R.string.row_hidden_apps), T.Gray, { navigate(SettingsRoute.HiddenApps) }, Modifier.weight(1f), index = 22,
                    subtitle = settings.hiddenApps.size.takeIf { it > 0 }?.toString(),
                )
                BentoTile(Icons.Rounded.Category, stringResource(R.string.row_change_icon), T.Blue, { navigate(SettingsRoute.AppIcons) }, Modifier.weight(1f), index = 23)
            }
        }
        item {
            Box(Modifier.padding(bottom = 12.dp)) {
                BentoTile(
                    Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.row_change_name), T.Green, { navigate(SettingsRoute.AppNames) },
                    Modifier.fillMaxWidth(), index = 24, kind = TileKind.Wide,
                )
            }
        }

        // ── Info ─────────────────────────────────────────────
        item { BentoHeader(pick(indo, "Info", "Info"), 25) }
        item {
            BentoRow {
                BentoTile(Icons.Rounded.Info, stringResource(R.string.row_about), T.Purple, { navigate(SettingsRoute.About) }, Modifier.weight(1f), index = 26, kind = TileKind.Compact)
                BentoTile(Icons.Rounded.Info, stringResource(R.string.row_version), T.Gray, {}, Modifier.weight(1f), index = 27, kind = TileKind.Compact, subtitle = actions.versionName)
            }
        }
        item {
            BentoRow {
                CheckUpdateTile(Modifier.weight(1f), index = 28)
                BentoTile(Icons.Rounded.Gavel, stringResource(R.string.row_license), T.Orange, { navigate(SettingsRoute.License) }, Modifier.weight(1f), index = 29, kind = TileKind.Compact, subtitle = "GPL-3.0+")
            }
        }
    }
}

/** Living header: a slowly drifting aurora gradient, floating light blobs and a breathing logo. */
@Composable
private fun HeroCard(isDefaultHome: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "hero")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flow",
    )
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val colors = listOf(SettingsStyle.Tile.Indigo, SettingsStyle.Tile.Purple, SettingsStyle.Tile.Pink, SettingsStyle.Tile.Blue)
    val shape = RoundedCornerShape(34.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .entrance(0)
            .bouncyClick(onClick)
            .glass(shape, tint = Color.Black.copy(alpha = 0.18f), profile = GlassProfile.Panel)
            .clip(shape)
            .drawBehind {
                val shift = size.width * flow
                // Aurora drifts over the glass body, translucent so the wallpaper still shows through.
                drawRect(
                    Brush.linearGradient(
                        colors = colors,
                        start = Offset(shift, 0f),
                        end = Offset(shift + size.width, size.height),
                        tileMode = TileMode.Mirror,
                    ),
                    alpha = 0.62f,
                )
                val a = flow * 2f * PI.toFloat()
                drawCircle(
                    Color.White.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * (0.82f + 0.08f * sin(a)), size.height * (0.22f + 0.10f * cos(a))),
                )
                drawCircle(
                    Color.White.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.30f,
                    center = Offset(size.width * (0.12f + 0.07f * cos(a)), size.height * (0.92f + 0.05f * sin(a))),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.graphicsLayer { scaleX = breathe; scaleY = breathe }) { AppLogo(size = 72.dp) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.app_name), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (isDefaultHome) R.string.default_home_active else R.string.default_home_select),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.28f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** "Finish setting up" banner with a pulsing red badge: the onboarding steps the user skipped. */
@Composable
private fun FinishSetupBanner(count: Int, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "badge")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .entrance(1)
            .bouncyClick(onClick)
            .glass(shape, tint = Tokens.Destructive.copy(alpha = 0.32f), profile = GlassProfile.Tile)
            .clip(shape)
            .border(1.dp, Tokens.Destructive.copy(alpha = 0.5f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .background(Tokens.Destructive, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(count.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.finish_setup_title),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
    }
}

/** Display name of a page transition. */
val PageTransition.labelRes: Int
    get() = when (this) {
        PageTransition.Default -> R.string.effect_default
        PageTransition.Rotation -> R.string.effect_rotation
        PageTransition.Flip -> R.string.effect_flip
        PageTransition.ZoomIn -> R.string.effect_zoom_in
        PageTransition.Alpha -> R.string.effect_alpha
    }
