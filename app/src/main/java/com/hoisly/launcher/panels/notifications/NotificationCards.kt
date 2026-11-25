package com.hoisly.launcher.panels.notifications

import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.drawable.toBitmap
import com.hoisly.launcher.R
import com.hoisly.launcher.ui.theme.Motion
import com.hoisly.launcher.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(22.dp)

/** One notification card: app icon, app name, title and text, and when it arrived. */
@Composable
internal fun NotificationCard(
    notification: CenterNotification,
    hideContent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hidden = hideContent || notification.secret
    val title = if (hidden) stringResource(R.string.notification_hidden_content) else notification.title
    Row(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Tokens.MenuBackground)
            .border(1.dp, Tokens.MenuStroke, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NotificationAppIcon(notification.packageName, Modifier.size(38.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.appLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                val time = remember(notification.postTime) {
                    DateUtils.getRelativeTimeSpanString(notification.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
                }
                Text(time, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            if (!title.isNullOrBlank()) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!hidden && !notification.text.isNullOrBlank()) {
                Text(notification.text, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Swipe sideways to dismiss: the card follows the finger, flies out when released far or fast
 * enough and springs back otherwise. Cards the app marked as not clearable do not move.
 */
@Composable
internal fun SwipeToClear(enabled: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - abs(offset.value) / width).coerceIn(0f, 1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                onDragStopped = { velocity ->
                    val dismiss = abs(offset.value) > width * 0.4f || abs(velocity) > FLING_PX_PER_S
                    if (dismiss) {
                        val direction = if (offset.value != 0f) offset.value else velocity
                        offset.animateTo(if (direction < 0f) -width * 1.2f else width * 1.2f, Motion.settle(), velocity)
                        onClear()
                    } else offset.animateTo(0f, Motion.settle(), velocity)
                },
            ),
    ) { content() }
}

private const val FLING_PX_PER_S = 1800f

private val iconCache = LruCache<String, ImageBitmap>(64)

/** The app's own icon, loaded off the main thread once per package and kept in a small cache. */
@Composable
internal fun NotificationAppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState(iconCache[packageName], packageName) {
        if (value == null) value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                    .also { iconCache.put(packageName, it) }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.1f))) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.matchParentSize()) }
    }
}

private const val ICON_PX = 96
