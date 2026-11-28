package com.akira.island.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import com.hoisly.launcher.R
import com.akira.island.core.LangScope
import com.akira.island.core.tr
import com.akira.island.data.SettingsRepo
import com.akira.island.data.LauncherVisibility
import com.akira.island.ui.screens.AnimScreen
import com.akira.island.ui.screens.SearchScreen
import com.akira.island.ui.screens.AppInfoScreen
import com.akira.island.ui.screens.BannersScreen
import com.akira.island.ui.screens.BanksScreen
import com.akira.island.ui.screens.BehaviorScreen
import com.akira.island.ui.screens.CapsuleScreen
import com.akira.island.ui.screens.CardsScreen
import com.akira.island.ui.screens.ColorsScreen
import com.akira.island.ui.screens.DiagScreen
import com.akira.island.ui.screens.EffectsScreen
import com.akira.island.ui.screens.EventsScreen
import com.akira.island.ui.screens.FeaturesScreen
import com.akira.island.ui.screens.GesturesScreen
import com.akira.island.ui.screens.HomeScreen
import com.akira.island.ui.screens.SupportScreen
import com.akira.island.ui.screens.IdeaScreen
import com.akira.island.ui.screens.InstructionScreen
import com.akira.island.ui.screens.KeepAliveScreen
import com.akira.island.ui.screens.LookScreen
import com.akira.island.ui.screens.LyricsScreen
import com.akira.island.ui.screens.LauncherScreen
import com.akira.island.ui.screens.NotificationsScreen
import com.akira.island.ui.screens.NotifAppsScreen
import com.akira.island.ui.screens.NotifDiagScreen
import com.akira.island.ui.screens.FontScreen
import com.akira.island.ui.screens.PayDiagScreen
import com.akira.island.ui.screens.PermissionsScreen
import com.akira.island.ui.screens.SizeScreen
import com.akira.island.ui.screens.TestsScreen
import com.akira.island.service.KeepAliveService
import com.hoisly.launcher.ui.glass.GlassMode
import com.hoisly.launcher.ui.glass.GlassProfile
import com.hoisly.launcher.ui.glass.GlassStyle
import com.hoisly.launcher.ui.glass.LocalGlassSource
import com.hoisly.launcher.ui.glass.LocalGlassStyle
import com.hoisly.launcher.ui.glass.WallpaperBlur
import com.hoisly.launcher.ui.glass.glass
import com.hoisly.launcher.ui.glass.GlassSource

enum class Screen {
    CARDS, EVENTS, GESTURES, LYRICS, SIZE, COLORS, INSTRUCTION, ABOUT, DIAG, LOOK, ANIM, EFFECTS, BANNERS,
    PERMISSIONS, TESTS, NOTIFICATIONS, BEHAVIOR, PAYDIAG, KEEPALIVE, BANKS, NOTIF_APPS, NOTIF_DIAG, FONT, EXTRAS, SEARCH, LAUNCHER, SUPPORT,
}

class MainActivity : ComponentActivity() {
    private var launcherSettingsRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsRepo.init(this)
        openSettingsFrom(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { IslandTheme { AppRoot(launcherSettingsRequest) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSettingsFrom(intent)
    }

    private fun openSettingsFrom(intent: Intent) {
        LauncherVisibility.onSettingsOpened(this, intent)
        if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES ||
            intent.action == Intent.ACTION_APPLICATION_PREFERENCES
        ) launcherSettingsRequest++
    }

    override fun onResume() {
        super.onResume()
        LauncherVisibility.refresh(this)
        // «Не выгружать остров»: из фона Android может не запустить службу — запускаем, пока приложение открыто
        KeepAliveService.sync(this)
    }
}

@Composable
private fun AppRoot(launcherSettingsRequest: Int) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    var forward by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var glassSource by remember { mutableStateOf<GlassSource?>(null) }
    LaunchedEffect(Unit) { if (tab !in 0..2) tab = 0 }
    val context = LocalContext.current
    LaunchedEffect(context) {
        val sharp = WallpaperBlur.loadPreview(context)
        glassSource = WallpaperBlur.load(context, sharp)
    }
    val open: (Screen) -> Unit = {
        forward = true
        stack = stack + it
    }
    val back: () -> Unit = {
        forward = false
        stack = stack.dropLast(1)
    }
    BackHandler(enabled = stack.isNotEmpty(), onBack = back)
    LaunchedEffect(launcherSettingsRequest) {
        if (launcherSettingsRequest > 0) {
            tab = 0
            forward = true
            stack = listOf(Screen.LAUNCHER)
        }
    }

    LangScope {
        val glassMode = if (Build.VERSION.SDK_INT >= 33 && glassSource?.sharp != null && glassSource?.body != null) {
            GlassMode.Liquid
        } else GlassMode.Frosted
        CompositionLocalProvider(
            LocalGlassSource provides glassSource,
            LocalGlassStyle provides GlassStyle(
                tintAlpha = 0.055f,
                edge = true,
                mode = glassMode,
                refraction = 1f,
                fallback = ComposeColor.White.copy(alpha = 0.12f),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                glassSource?.image?.let { wallpaper ->
                    Image(
                        bitmap = wallpaper,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Box(Modifier.fillMaxSize().background(ComposeColor(0xFF77745A)))

                Row(Modifier.fillMaxSize()) {
                    SideRail(tab = tab, onSelect = { tab = it }, visible = stack.isEmpty())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val key = stack.lastOrNull()?.name ?: "tab$tab"
                        AnimatedContent(
                            targetState = key,
                            transitionSpec = {
                                if (targetState.startsWith("tab") && initialState.startsWith("tab")) {
                                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                                } else if (forward) {
                                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
                                } else {
                                    (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                                }
                            },
                            label = "nav",
                        ) { k ->
                            when (k) {
                                Screen.SEARCH.name -> SearchScreen(searchQuery, { searchQuery = it }, back, open)
                                "tab0" -> HomeScreen(open)
                                "tab1" -> FeaturesScreen(open)
                                "tab2" -> CapsuleScreen(open)
                                Screen.EXTRAS.name -> com.akira.island.ui.screens.ExtrasScreen(back)
                                Screen.CARDS.name -> CardsScreen(back) { open(Screen.INSTRUCTION) }
                                Screen.EVENTS.name -> EventsScreen(back)
                                Screen.GESTURES.name -> GesturesScreen(back)
                                Screen.LYRICS.name -> LyricsScreen(back)
                                Screen.SIZE.name -> SizeScreen(back)
                                Screen.COLORS.name -> ColorsScreen(back)
                                Screen.INSTRUCTION.name -> InstructionScreen(back)
                                Screen.ABOUT.name -> IdeaScreen(back)
                                Screen.SUPPORT.name -> SupportScreen(back)
                                Screen.DIAG.name -> DiagScreen(back)
                                Screen.PAYDIAG.name -> PayDiagScreen(back)
                                Screen.KEEPALIVE.name -> KeepAliveScreen(back)
                                Screen.LAUNCHER.name -> LauncherScreen(back)
                                Screen.BANKS.name -> BanksScreen(back)
                                Screen.LOOK.name -> LookScreen(back)
                                Screen.ANIM.name -> AnimScreen(back)
                                Screen.EFFECTS.name -> EffectsScreen(back)
                                Screen.BANNERS.name -> BannersScreen(back)
                                Screen.PERMISSIONS.name -> PermissionsScreen(back, open)
                                Screen.TESTS.name -> TestsScreen(back)
                                Screen.NOTIFICATIONS.name -> NotificationsScreen(back, open)
                                Screen.NOTIF_APPS.name -> NotifAppsScreen(back)
                                Screen.NOTIF_DIAG.name -> NotifDiagScreen(back)
                                Screen.FONT.name -> FontScreen(back)
                                Screen.BEHAVIOR.name -> BehaviorScreen(back)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Three primary destinations live in a slim vertical glass rail; About is no longer a tab. */
@Composable
private fun SideRail(tab: Int, onSelect: (Int) -> Unit, visible: Boolean) {
    if (!visible) return
    val titles = listOf(tr("Beranda", "Home"), tr("Fitur", "Features"), tr("Tampilan", "Appearance"))
    val icons = listOf(
        ImageVector.vectorResource(R.drawable.ic_tab_island),
        Icons.Rounded.Widgets,
        Icons.Rounded.Palette,
    )
    Column(
        Modifier
            .width(82.dp)
            .fillMaxHeight()
            .padding(start = 7.dp, top = 58.dp, end = 5.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Column(
            Modifier
                .fillMaxHeight(0.82f)
                .fillMaxWidth()
                .glass(RoundedCornerShape(32.dp), profile = GlassProfile.Panel)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        ) {
            titles.forEachIndexed { index, title ->
                val selected = tab == index
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .then(if (selected) Modifier.glass(RoundedCornerShape(22.dp), profile = GlassProfile.SmallButton) else Modifier)
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icons[index], title, tint = ComposeColor.White, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(7.dp))
                    Text(title, color = ComposeColor.White.copy(alpha = if (selected) 1f else 0.78f), fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}
