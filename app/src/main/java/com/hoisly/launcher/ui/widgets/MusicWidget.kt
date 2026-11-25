package com.hoisly.launcher.ui.widgets

import android.content.Context
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.hoisly.launcher.R
import com.hoisly.launcher.island.IslandAccess
import com.hoisly.launcher.panels.control.NowPlaying
import com.hoisly.launcher.panels.control.readNowPlaying
import com.hoisly.launcher.panels.control.startQuietly
import com.hoisly.launcher.ui.icon.IosIconPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** What the Music widget shows: the current media session, and whether we may read sessions at all. */
internal data class MusicState(val playing: NowPlaying? = null, val access: Boolean = false)

/**
 * Shared between the Music widget (which polls), its name under the widget and the home-screen
 * tap handler. Only the widget on screen refreshes it; nothing polls while no Music widget is shown.
 */
internal object MediaWidgetBridge {
    private val mutable = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = mutable

    @Volatile
    private var appContext: Context? = null

    fun publish(context: Context, next: MusicState) {
        appContext = context.applicationContext
        val old = mutable.value
        // Keep the old object while nothing changed, so the widget does not recompose for nothing.
        mutable.value = if (next.access == old.access && (next.playing?.sameAs(old.playing) ?: (old.playing == null))) old else next
    }

    /** A tap at [x], [y] (0..1 over the widget): the play button toggles playback, anywhere else opens the player. */
    fun onTap(x: Float, y: Float) {
        val context = appContext ?: return
        val current = mutable.value
        val playing = current.playing
        when {
            !current.access -> context.startQuietly(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            playing == null -> Unit
            x > PLAY_ZONE && y > PLAY_ZONE -> playing.playPause()
            else -> playing.openPlayer(context)
        }
    }

    private const val PLAY_ZONE = 0.58f
}

@Composable
internal fun rememberMusicState(): MusicState {
    val context = LocalContext.current.applicationContext
    // Only the polling lives here; the shared bridge is observed, so every reader sees each update.
    LaunchedEffect(context) {
        while (true) {
            val access = IslandAccess.read(context).notifications
            val playing = if (access) withContext(Dispatchers.Default) { readNowPlaying(context) } else null
            MediaWidgetBridge.publish(context, MusicState(playing, access))
            delay(POLL_MS)
        }
    }
    val state by MediaWidgetBridge.state.collectAsState()
    return state
}

private const val POLL_MS = 1000L

/** Name under the widget: the player's own name ("Spotify") while something plays, "Music" otherwise. */
@Composable
internal fun musicWidgetLabel(state: MusicState): String {
    val context = LocalContext.current
    val packageName = state.playing?.packageName
    val label = remember(packageName) {
        packageName?.let {
            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
        }
    }
    return label ?: stringResource(R.string.widget_music)
}

@Composable
private fun rememberPlayerIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = packageName?.let { pkg ->
            withContext(Dispatchers.Default) {
                IosIconPack.load(context.resources, pkg, 96)
                    ?: runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }
        }
    }
    return icon
}

private val CardTop = Color(0xFF111A4A)
private val CardBottom = Color(0xFF070B22)

/**
 * Small "Now Playing" widget, like the Spotify widget of iOS: album art top-left, the player's
 * logo top-right, the song in bold and its artist under it, and a round play/pause button.
 * Taps are handled by the home screen (see [MediaWidgetBridge.onTap]).
 */
@Composable
internal fun MusicWidget() {
    val music = rememberMusicState()
    val playing = music.playing
    val player = rememberPlayerIcon(playing?.packageName)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CardTop, CardBottom)))) {
        val art = playing?.artwork
        Box(
            Modifier
                .padding(start = 14.dp, top = 14.dp)
                .size(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(26.dp))
        }
        Box(
            Modifier
                .padding(top = 14.dp, end = 14.dp)
                .size(24.dp)
                .clip(CircleShape)
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.Center,
        ) {
            if (player != null) Image(player, null, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = playing?.title ?: stringResource(if (music.access) R.string.widget_music_idle else R.string.widget_music_allow),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                playing?.artist?.let {
                    Text(it, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A2257))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
