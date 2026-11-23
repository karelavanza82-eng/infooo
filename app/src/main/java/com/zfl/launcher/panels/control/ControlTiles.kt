package com.zfl.launcher.panels.control

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.launcherHaptics

private val TileOff = Color.White.copy(alpha = 0.16f)
private val TileCorner = 24.dp

/** Accent of each tile when on, taken from the iOS palette the settings screens already use. */
internal object ControlColors {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Purple = Color(0xFF5E5CE6)
    val Red = Color(0xFFFF453A)
    val White = Color.White
}

/**
 * A control tile that shows a state read from the system. [active] is that state, never a
 * local guess. [round] draws the circular buttons of the connectivity card. A tap switches or
 * opens; a long press runs [onLongClick] (the tile's own system screen), with a haptic for each.
 */
@Composable
internal fun ControlTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    showsState: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = launcherHaptics()
    var pressed by remember { mutableStateOf(false) }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentActive by rememberUpdatedState(active)
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.press(), label = "tileScale")
    val background by animateColorAsState(if (active) activeColor else TileOff, Motion.control(), label = "tileColor")
    val tint = if (active && activeColor == ControlColors.White) Color.Black.copy(alpha = 0.8f) else Color.White
    val state = if (!showsState) null else stringResource(if (active) R.string.control_on else R.string.control_off)
    val shape = if (round) CircleShape else RoundedCornerShape(TileCorner)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), shape)
            .pointerInput(showsState) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptic.performHapticFeedback(
                            when {
                                !showsState -> Haptics.Tick
                                currentActive -> Haptics.ToggleOff
                                else -> Haptics.ToggleOn
                            },
                        )
                        currentClick()
                    },
                    onLongPress = {
                        currentLongClick?.let { action ->
                            haptic.performHapticFeedback(Haptics.Lift)
                            action()
                        }
                    },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = label
                if (state != null) stateDescription = state
                semanticsOnClick(label = null) { currentClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

/** Brightness and volume: a tall pill filled from the bottom, dragged or tapped like iOS. */
@Composable
internal fun VerticalControlSlider(
    value: Float,
    icon: ImageVector,
    description: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    iconFor: ((Float) -> ImageVector)? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val change by rememberUpdatedState(onChange)
    val shown = dragging ?: value
    val haptic = launcherHaptics()
    // Like iOS, the pill grows a little under the finger and ticks when it reaches either end.
    val grow by animateFloatAsState(if (dragging != null) 1.05f else 1f, Motion.pop(), label = "sliderGrow")
    val atEnd = dragging != null && (shown <= 0f || shown >= 1f)
    LaunchedEffect(atEnd) { if (atEnd) haptic.performHapticFeedback(Haptics.Tick) }
    Box(
        modifier
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .clip(RoundedCornerShape(50))
            .background(GlassFill)
            .drawBehind {
                val height = size.height * shown.coerceIn(0f, 1f)
                drawRect(Color.White.copy(alpha = 0.95f), Offset(0f, size.height - height), Size(size.width, height))
            }
            .border(0.8.dp, GlassEdge, RoundedCornerShape(50))
            .pointerInput(Unit) {
                detectTapGestures { change((1f - it.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                fun at(y: Float) = (1f - y / size.height).coerceIn(0f, 1f)
                detectVerticalDragGestures(
                    onDragStart = { dragging = at(it.y).also(change) },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                    onVerticalDrag = { pointer, _ ->
                        pointer.consume()
                        dragging = at(pointer.position.y).also(change)
                    },
                )
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target -> change(target.coerceIn(0f, 1f)); true }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            iconFor?.invoke(shown) ?: icon,
            contentDescription = null,
            tint = if (shown > 0.14f) Color.Black.copy(alpha = 0.6f) else Color.White,
            modifier = Modifier.padding(bottom = 16.dp).size(26.dp),
        )
    }
}

/** Now Playing: the real media session, with its transport controls. */
@Composable
internal fun MediaCard(
    playing: NowPlaying?,
    hasAccess: Boolean,
    onAllowAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(TileCorner + 2.dp))
            .background(TileOff)
            .then(if (!hasAccess) Modifier.clickable(role = Role.Button, onClick = onAllowAccess) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (playing == null) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
            Text(
                stringResource(if (hasAccess) R.string.control_media_idle else R.string.control_media_allow),
                color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = playing.artwork
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f))) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
            }
        }
        Column {
            Text(playing.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playing.artist?.let { Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TransportButton(Icons.Rounded.SkipPrevious, stringResource(R.string.control_previous), playing::previous)
            TransportButton(
                if (playing.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (playing.playing) R.string.control_pause else R.string.control_play),
                playing::playPause,
            )
            TransportButton(Icons.Rounded.SkipNext, stringResource(R.string.control_next), playing::next)
        }
    }
}

@Composable
internal fun TransportButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, Motion.press(), label = "transport")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

/**
 * Now Playing as the wide pill of iOS 26: the cover, the song and artist, then pause, fast
 * forward and the output button. Idle, it says so (or asks for the notification access it needs).
 */
@Composable
internal fun NowPlayingPill(
    playing: NowPlaying?,
    hasAccess: Boolean,
    onAllowAccess: () -> Unit,
    onOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(GlassFill)
            .border(0.8.dp, GlassEdge, RoundedCornerShape(50))
            .then(if (!hasAccess) Modifier.clickable(role = Role.Button, onClick = onAllowAccess) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val art = playing?.artwork
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                playing?.title ?: stringResource(if (hasAccess) R.string.control_media_idle else R.string.control_media_allow),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            playing?.artist?.let { Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (playing != null) {
            TransportButton(
                if (playing.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (playing.playing) R.string.control_pause else R.string.control_play),
                playing::playPause,
            )
            TransportButton(Icons.Rounded.FastForward, stringResource(R.string.control_next), playing::next)
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(role = Role.Button, onClick = onOutput),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Cast, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}
