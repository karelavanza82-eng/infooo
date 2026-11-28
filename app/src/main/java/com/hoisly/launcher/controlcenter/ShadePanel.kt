package com.hoisly.launcher.controlcenter

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.min

private fun frac(v: Float, start: Float, end: Float) = ((v - start) / (end - start)).coerceIn(0f, 1f)

/** Content margins taken from the reference: 34 on the left, the page rail on the right. */
private const val LEFT = 34f
private const val CONTENT_W = 291f

/**
 * The Hoisly shade has a notification stage and a full Control Center stage. Three Control
 * Center pages are switched from the rail on the right edge:
 * heart = the main layout, music = players, antenna = connectivity.
 */
@Composable
fun ShadePanel(controller: ShadeController, tiles: TileRepository, host: ShadeService, blurEnabled: Boolean) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val palette = remember(config.uiMode, config.hashCode()) { ShadePalette.from(ctx) }
    val states by tiles.state.collectAsState()
    val placed by tiles.placed.collectAsState()
    val media by MediaRepo.items.collectAsState()
    var page by remember { mutableIntStateOf(0) }
    var showLayoutControls by remember { mutableStateOf(false) }
    var uiScale by remember { mutableFloatStateOf(ctx.getSharedPreferences("hoisly_cc_ui", 0).getFloat("scale", 1f).coerceIn(0.78f, 1.12f)) }
    var iconScale by remember { mutableFloatStateOf(ctx.getSharedPreferences("hoisly_cc_ui", 0).getFloat("icon_scale", 1f).coerceIn(0.7f, 1.35f)) }
    val expandedControl = controller.expansion.value > 1.01f
    val closed by remember(controller) { derivedStateOf { controller.expansion.value == 0f } }
    LaunchedEffect(closed) { if (closed) page = 0 }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Scale against both axes. The old width-only scale made the fixed 600dp design
        // overflow and appear enlarged in landscape. Center a fitted reference canvas.
        val unit = minOf(maxWidth.value / 360f, maxHeight.value / 600f).coerceAtLeast(0.35f) * uiScale
        CompositionLocalProvider(LocalUnit provides unit, LocalPalette provides palette, LocalTileIconScale provides iconScale) {
            val pxPerU = with(LocalDensity.current) { 1.u.toPx() }
            val scrimAlpha = if (blurEnabled) 0.62f else 0.96f

            // Dark scrim over the blurred backdrop.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = frac(controller.expansion.value.coerceIn(0f, 1f), 0f, 0.4f) * scrimAlpha }
                    .background(Color(0xFF0B0B0C))
                    .panelDrag(controller)
                    .tapToClose(controller),
            )

            Box(
                Modifier
                    .size(width = 360.u, height = 600.u)
                    .align(Alignment.Center)
                    .panelDrag(controller)
                    .tapToClose(controller)
                    .graphicsLayer {
                        val o = controller.expansion.value.coerceIn(0f, 1f)
                        alpha = frac(o, 0.05f, 0.6f)
                        translationY = -(1f - o) * 48f * pxPerU
                        val s = 0.94f + 0.06f * o
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            ) {
                Crossfade(targetState = expandedControl, label = "shadeMode") { fullControl ->
                    if (fullControl) {
                        Crossfade(targetState = page, label = "page") { pg ->
                            when (pg) {
                                0 -> MainPage(states, placed, media, tiles, host)
                                1 -> MusicPage(media, host)
                                else -> ConnectivityPage(states, tiles, host)
                            }
                        }
                        PageRail(page) { page = it }
                        Box(
                            Modifier.offset(326.u, 10.u).size(30.u).clip(CircleShape)
                                .clickable { showLayoutControls = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Tune, "Atur Control Center", tint = Color.White, modifier = Modifier.size(19.u)) }
                    } else {
                        NotificationCenterPage(host, Modifier.fillMaxSize())
                    }
                }
            }
            if (showLayoutControls) {
                Dialog(onDismissRequest = { showLayoutControls = false }) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                            .glass(RoundedCornerShape(28.dp), Color(0xB8202024))
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Tampilan Hoisly", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("Ukuran seluruh elemen", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                        Slider(
                            value = uiScale, onValueChange = {
                                uiScale = it
                                ctx.getSharedPreferences("hoisly_cc_ui", 0).edit().putFloat("scale", it).apply()
                            }, valueRange = 0.78f..1.12f,
                        )
                        Text("${(uiScale * 100).toInt()}%", color = Color.White, fontSize = 14.sp)
                        Text("Ukuran ikon", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                        Slider(
                            value = iconScale, onValueChange = {
                                iconScale = it
                                ctx.getSharedPreferences("hoisly_cc_ui", 0).edit().putFloat("icon_scale", it).apply()
                            }, valueRange = 0.7f..1.35f,
                        )
                        Text("${(iconScale * 100).toInt()}%", color = Color.White, fontSize = 14.sp)
                        Text(
                            "Edit susunan tile", color = Color.White, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(Color(0x3330D158)).clickable {
                                    showLayoutControls = false
                                    host.launch(Intent(ctx, TileEditActivity::class.java))
                                }.padding(14.dp),
                        )
                        Text("Selesai", color = Color(0xFF30D158), fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.End).clickable { showLayoutControls = false }.padding(8.dp))
                    }
                }
            }
        }
    }
}

// Notification Center is the first stage of the Hoisly shade. It uses the same live
// NotificationListenerService data as the launcher badge feed; no demo-only list is shown.
@Composable
private fun NotificationCenterPage(host: ShadeService, modifier: Modifier = Modifier) {
    val items by NotifListener.items.collectAsState()
    var openMenu by remember { mutableStateOf(false) }
    Column(modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notifikasi", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text("Hapus semua", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(30.dp)).clickable { NotifListener.instance?.dismissAll() }.padding(8.dp))
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NotificationsNone, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Belum ada notifikasi", color = Color.White, fontSize = 17.sp)
                    Text("Notifikasi baru akan muncul di sini", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(items, key = { it.key }) { item ->
                    NotificationCard(
                        item = item,
                        onOpen = { host.sendPendingIntent(it.contentIntent) },
                        onDismiss = { NotifListener.instance?.dismiss(it) },
                        onAction = { n, index -> n.actions.getOrNull(index)?.actionIntent?.let(host::sendPendingIntent) },
                        onSnooze = { NotifListener.instance?.snooze(it, 5 * 60 * 1000L) },
                        onSettings = { n -> host.launch(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, n.pkg)) },
                        menuOpen = openMenu,
                        onMenuOpenChange = { openMenu = it },
                    )
                }
            }
        }
    }
}

// region pages

@Composable
private fun MainPage(states: Map<String, TileUi>, placed: PlacedTiles, media: List<MediaItem>, tiles: TileRepository, host: ShadeService) {
    Box(Modifier.fillMaxSize()) {
        IosStatusRow(Modifier.at(LEFT, 10f, CONTENT_W, 24f))

        // Music card (swipe sideways when more than one player is active).
        if (media.size > 1) {
            val pager = rememberPagerState { media.size }
            HorizontalPager(
                state = pager, modifier = Modifier.at(LEFT, 47f, CONTENT_W, 139f), pageSpacing = 10.u,
                key = { media[it].token.hashCode() },
            ) { i -> IosMediaCard(media[i], host, Modifier.fillMaxSize()) }
        } else {
            IosMediaCard(media.firstOrNull(), host, Modifier.at(LEFT, 47f, CONTENT_W, 139f))
        }

        ConnectivityBlock(states, tiles, host, Modifier.at(LEFT, 200f, 138f, 138f))
        Sliders(brightnessModifier = Modifier.at(187f, 200f, 62f, 138f), volumeModifier = Modifier.at(263f, 200f, 62f, 138f))

        placed.highlight.getOrNull(0)?.let { t ->
            states[t.id]?.let { s -> WidePill(s, { tiles.click(t, host) }, { t.longClick(host) }, Modifier.at(LEFT, 353f, 138f, 62f)) }
        }
        placed.highlight.getOrNull(1)?.let { t ->
            states[t.id]?.let { s -> WidePill(s, { tiles.click(t, host) }, { t.longClick(host) }, Modifier.at(187f, 353f, 138f, 62f)) }
        }

        TileRow(placed.grid.take(4), states, tiles, host, 430f)
        TileRow(placed.grid.drop(4).take(4), states, tiles, host, 506f)
    }
}

@Composable
private fun TileRow(row: List<Tile>, states: Map<String, TileUi>, tiles: TileRepository, host: ShadeService, y: Float) {
    // Four 62dp circles spread across the 291dp content width.
    val step = (CONTENT_W - 62f) / 3f
    row.forEachIndexed { i, t ->
        val s = states[t.id] ?: return@forEachIndexed
        Box(Modifier.offset((LEFT + i * step).u, y.u)) {
            GlassCircleTile(s, 62.u, onClick = { tiles.click(t, host) }, onLongClick = { t.longClick(host) })
        }
    }
}

/** The 2x2 connectivity block: airplane, hotspot, a big Wi-Fi button and four small ones. */
@Composable
private fun ConnectivityBlock(states: Map<String, TileUi>, tiles: TileRepository, host: ShadeService, modifier: Modifier) {
    fun click(id: String) { tiles.byId(id)?.let { tiles.click(it, host) } }
    fun long(id: String) { tiles.byId(id)?.longClick(host) }

    Box(modifier.glass(RoundedCornerShape(36.u), G.fill)) {
        ConnectivityButton(
            states["airplane"], G.orange, 48.u, 25.u, { click("airplane") }, { long("airplane") },
            Modifier.offset(14.u, 14.u), icon = Icons.Rounded.AirplanemodeActive, iconRotate = 45f,
        )
        ConnectivityButton(
            states["hotspot"], G.green, 48.u, 27.u, { click("hotspot") }, { long("hotspot") },
            Modifier.offset(76.u, 14.u), icon = Icons.Rounded.WifiTethering,
        )
        ConnectivityButton(
            states["wifi"], G.blue, 50.u, 29.u, { click("wifi") }, { long("wifi") },
            Modifier.offset(13.u, 74.u), icon = Icons.Rounded.Wifi,
        )
        ConnectivityButton(
            states["cell"], G.green, 23.u, 14.u, { click("cell") }, { long("cell") },
            Modifier.offset(75.u, 75.u), icon = Icons.Rounded.SignalCellularAlt,
        )
        ConnectivityButton(
            states["bt"], G.blue, 23.u, 13.u, { click("bt") }, { long("bt") },
            Modifier.offset(103.u, 75.u), icon = Icons.Rounded.Bluetooth,
        )
        ConnectivityButton(
            states["datasaver"], G.blue, 23.u, 13.u, { click("datasaver") }, { long("datasaver") },
            Modifier.offset(75.u, 103.u), icon = Icons.Rounded.LinkOff,
        )
        // The globe opens Android's internet panel.
        GlassButton(
            Modifier.offset(103.u, 103.u).size(23.u), CircleShape, G.fillDeep,
            onClick = { host.launch(settingsIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
        ) {
            Icon(Icons.Rounded.Public, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.u))
        }
    }
}

@Composable
private fun MusicPage(media: List<MediaItem>, host: ShadeService) {
    Column(Modifier.offset(LEFT.u, 47.u).width(CONTENT_W.u)) {
        if (media.isEmpty()) {
            IosMediaCard(null, host, Modifier.fillMaxWidth().height(139.u))
        } else {
            media.take(3).forEach { m ->
                IosMediaCard(m, host, Modifier.fillMaxWidth().height(139.u))
                Spacer(Modifier.height(14.u))
            }
        }
    }
}

@Composable
private fun ConnectivityPage(states: Map<String, TileUi>, tiles: TileRepository, host: ShadeService) {
    val items = listOf(
        "wifi" to G.blue, "bt" to G.blue, "cell" to G.green, "airplane" to G.orange,
        "hotspot" to G.green, "nfc" to G.blue, "location" to G.blue, "datasaver" to G.blue,
    )
    val step = (CONTENT_W - 62f) / 3f
    Box(Modifier.fillMaxSize()) {
        items.forEachIndexed { i, (id, color) ->
            val s = states[id] ?: return@forEachIndexed
            val x = LEFT + (i % 4) * step
            val y = 47f + (i / 4) * 112f
            ConnectivityButton(
                s, color, 62.u, 29.u,
                onClick = { tiles.byId(id)?.let { tiles.click(it, host) } },
                onLongClick = { tiles.byId(id)?.longClick(host) },
                modifier = Modifier.offset(x.u, y.u),
                iconRotate = if (id == "airplane") 45f else 0f,
            )
            Text(
                s.label, color = Color.White, fontSize = 12.us, lineHeight = 14.us, fontFamily = Op.Body,
                textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier.offset((x - 7f).u, (y + 67f).u).width(76.u),
            )
        }
    }
}

/** The three page icons down the right edge: favourites, music, connectivity. */
@Composable
private fun PageRail(page: Int, onPage: (Int) -> Unit) {
    val icons = listOf(Icons.Rounded.Favorite, Icons.Rounded.MusicNote, Icons.Rounded.CellTower)
    val ys = listOf(256f, 306f, 354f)
    icons.forEachIndexed { i, icon ->
        Box(
            Modifier
                .offset(328.u, ys[i].u)
                .size(28.u)
                .clip(CircleShape)
                .clickable { onPage(i) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (page == i) Color.White else Color.White.copy(alpha = 0.42f), modifier = Modifier.size(if (i == 0) 17.u else 18.u))
        }
    }
}

// endregion

// region sliders

@Composable
private fun rememberTick(): Long {
    val now by produceState(0L) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1500)
        }
    }
    return now
}

/** Brightness (sun, yellow) and media volume (speaker, blue): white fills rising from the bottom of frosted pills. */
@Composable
private fun Sliders(brightnessModifier: Modifier, volumeModifier: Modifier) {
    val ctx = LocalContext.current
    val tick = rememberTick()
    var bright by remember { mutableFloatStateOf(Brightness.read(ctx)) }
    var auto by remember { mutableStateOf(Brightness.isAuto(ctx)) }
    var vol by remember { mutableFloatStateOf(MediaVolume.read(ctx)) }
    var muted by remember { mutableStateOf(MediaVolume.isMuted(ctx)) }
    LaunchedEffect(tick) {
        bright = Brightness.read(ctx); auto = Brightness.isAuto(ctx)
        vol = MediaVolume.read(ctx); muted = MediaVolume.isMuted(ctx)
    }
    val pill = RoundedCornerShape(50)
    val track = Color(0x47FFFFFF)

    VerticalSlider(
        value = bright,
        onValueChange = { bright = it; Brightness.write(ctx, it) },
        icon = Icons.Rounded.WbSunny,
        iconTint = G.yellow,
        onIconClick = { auto = !auto; Brightness.setAuto(ctx, auto) },
        modifier = brightnessModifier.rim(pill),
        trackColor = track, fillColor = G.white, idleTint = Color.White.copy(alpha = 0.75f),
        heightU = 138f, iconDp = 28f,
    )
    VerticalSlider(
        value = if (muted) 0f else vol,
        onValueChange = { vol = it; muted = false; MediaVolume.write(ctx, it) },
        icon = if (muted || vol == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
        iconTint = G.cyan,
        onIconClick = { MediaVolume.toggleMute(ctx); muted = MediaVolume.isMuted(ctx) },
        steps = MediaVolume.steps(ctx),
        modifier = volumeModifier.rim(pill),
        trackColor = track, fillColor = G.white, idleTint = Color.White.copy(alpha = 0.75f),
        heightU = 138f, iconDp = 30f,
    )
}

// endregion

// region gestures

/** Closes the shade on a tap that no child handled. */
private fun Modifier.tapToClose(controller: ShadeController) = pointerInput(controller) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation()
        if (up != null && (up.position - down.position).getDistance() < 20f) controller.close()
    }
}

/** Vertical drags on empty space: pull down opens, push up closes. Single stage. */
private fun Modifier.panelDrag(controller: ShadeController) = pointerInput(controller) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var started = false
        val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            controller.dragBy(over, maxStage = 1f)
            started = true
        }
        if (slopChange != null || started) {
            verticalDrag(slopChange?.id ?: down.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                controller.dragBy(change.position.y - change.previousPosition.y, maxStage = 1f)
                change.consume()
            }
            controller.settle(tracker.calculateVelocity().y, maxStage = 1f)
        }
    }
}

// endregion
