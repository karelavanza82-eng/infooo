package com.hoisly.launcher.panels.control

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.hoisly.launcher.island.IslandAccess

/** The media session the music widget shows and drives. */
class NowPlaying(
    val title: String,
    val artist: String?,
    val playing: Boolean,
    val artwork: ImageBitmap?,
    private val controller: MediaController,
    /** Package of the player app (Spotify, YouTube Music...). */
    val packageName: String = controller.packageName,
    val durationMs: Long = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
) {
    fun positionMs(): Long = controller.playbackState?.position?.coerceAtLeast(0L) ?: 0L

    fun playPause() = if (playing) controller.transportControls.pause() else controller.transportControls.play()
    fun next() = controller.transportControls.skipToNext()
    fun previous() = controller.transportControls.skipToPrevious()
    fun seekBy(deltaMs: Long) {
        val position = controller.playbackState?.position ?: return
        controller.transportControls.seekTo((position + deltaMs).coerceAtLeast(0L))
    }

    /** Opens the player's own screen (its session activity, or its launcher activity). */
    fun openPlayer(context: Context) {
        val pending = controller.sessionActivity
        val sent = pending != null && runCatching { pending.send() }.isSuccess
        if (!sent) {
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startQuietly(it) }
        }
    }

    /** Same song, state and player: a poll that finds this keeps the old object (and its controller). */
    fun sameAs(other: NowPlaying?): Boolean = other != null &&
        title == other.title && artist == other.artist && playing == other.playing &&
        packageName == other.packageName && (artwork == null) == (other.artwork == null)
}

/**
 * The playing session, or the first one, from the media sessions Android lists for apps with
 * notification access. Null without that access (a [SecurityException]) or without a session.
 * Called while the panel is visible only; no listener stays registered.
 */
fun readNowPlaying(context: Context): NowPlaying? {
    val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
    val sessions = runCatching { manager.getActiveSessions(IslandAccess.component(context)) }.getOrNull().orEmpty()
    val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: sessions.firstOrNull() ?: return null
    val metadata = controller.metadata ?: return null
    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
    val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    return NowPlaying(
        title = title,
        artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
        playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
        artwork = art?.asImageBitmap(),
        controller = controller,
    )
}

/** Starts [intent] from the launcher; false when no app handles it. */
internal fun Context.startQuietly(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
