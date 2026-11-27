package com.hoisly.launcher.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoisly.launcher.ui.glass.GlassProfile
import com.hoisly.launcher.ui.glass.glass
import kotlinx.coroutines.delay

/** Shapes of a bento tile: tall (half width), wide (full width) and compact (half width, low). */
internal enum class TileKind { Tall, Wide, Compact }

/**
 * Entrance animation: the item springs up from below, fading and scaling in, staggered by [index].
 * It plays once; scrolling the item back into view does not replay it.
 */
@Composable
internal fun Modifier.entrance(index: Int): Modifier {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
            played = true
        }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 48.dp.toPx()
        val s = 0.9f + 0.1f * p
        scaleX = s
        scaleY = s
    }
}

/** Click with a springy squash while the finger is down. */
@Composable
internal fun Modifier.bouncyClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
private fun IconChip(icon: ImageVector, pulse: Boolean, size: Dp = 42.dp) {
    val transition = rememberInfiniteTransition(label = "chip")
    val beat by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "chipBeat",
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val s = if (pulse) beat else 1f
                scaleX = s
                scaleY = s
            }
            .background(Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.54f))
    }
}

/** One colourful gradient tile of the new settings home. */
@Composable
internal fun BentoTile(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    subtitle: String? = null,
    kind: TileKind = TileKind.Tall,
    pulse: Boolean = false,
) {
    val shape = RoundedCornerShape(if (kind == TileKind.Compact) 22.dp else 28.dp)
    val height = when (kind) {
        TileKind.Tall -> 128.dp
        TileKind.Wide -> 92.dp
        TileKind.Compact -> 76.dp
    }
    // Soft sheen on top of the glass so each tile keeps some depth.
    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, lerp(color, Color.Black, 0.5f).copy(alpha = 0.22f)))
    Box(
        modifier
            .height(height)
            .entrance(index)
            .bouncyClick(onClick)
            .glass(shape, tint = color.copy(alpha = 0.42f), profile = GlassProfile.Tile)
            .clip(shape)
            .background(sheen)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        if (kind != TileKind.Compact) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.13f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 18.dp, y = 18.dp)
                    .size(if (kind == TileKind.Wide) 84.dp else 92.dp)
                    .graphicsLayer { rotationZ = -14f },
            )
        }
        when (kind) {
            TileKind.Tall -> Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    IconChip(icon, pulse)
                    Spacer(Modifier.weight(1f))
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.26f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    title,
                    color = Color.White,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TileKind.Wide -> Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(icon, pulse, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(26.dp))
            }
            TileKind.Compact -> Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(icon, pulse, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Section title with a small gradient bar, slides in like the tiles. */
@Composable
internal fun BentoHeader(text: String, index: Int) {
    Row(
        Modifier.entrance(index).padding(start = 4.dp, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 18.dp, height = 5.dp)
                .background(Brush.horizontalGradient(listOf(SettingsStyle.Tile.Pink, SettingsStyle.Tile.Purple)), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
    }
}

/** Two (or more) tiles side by side with an even gap. */
@Composable
internal fun BentoRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
