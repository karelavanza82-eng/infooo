package com.zfl.launcher.panels.control

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.launcherHaptics

/** The frosted look of iOS 26 controls: a light translucent fill with a bright upper edge. */
internal val GlassFill: Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.17f)))
internal val GlassEdge: Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.08f)))
private val ActiveFill = Color.White.copy(alpha = 0.96f)
private val DarkText = Color(0xFF1C1C1E)
private val GrayText = Color(0xFF6D6D72)

/**
 * Base of every control in the iOS 26 Control Center: glass when off, white when on, with the
 * press squash, the haptics, and the accessibility role and state of a button. [content] is
 * drawn over the surface.
 */
@Composable
internal fun ControlSurface(
    modifier: Modifier,
    shape: Shape,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    showsState: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = launcherHaptics()
    var pressed by remember { mutableStateOf(false) }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentActive by rememberUpdatedState(active)
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, Motion.press(), label = "controlScale")
    val white by animateFloatAsState(if (active) 1f else 0f, Motion.control(), label = "controlWhite")
    val state = if (!showsState) null else stringResource(if (active) R.string.control_on else R.string.control_off)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(GlassFill)
            .background(ActiveFill.copy(alpha = ActiveFill.alpha * white))
            .border(0.8.dp, GlassEdge, shape)
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
        content = content,
    )
}

/**
 * A round control button. Off: glass with a white glyph. On: a white disc with the glyph in
 * [activeColor], like the silent and rotation-lock buttons of iOS 26.
 */
@Composable
internal fun GlassCircle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showsState: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    glyphSize: Dp = 26.dp,
) {
    ControlSurface(modifier, CircleShape, label, active, onClick, showsState, onLongClick) {
        Icon(icon, contentDescription = null, tint = if (active) activeColor else Color.White, modifier = Modifier.size(glyphSize))
    }
}

/**
 * A wide control with a round glyph, a title and a second line: the Bluetooth, Wi-Fi, Focus and
 * AirDrop pills. White when on (dark text), glass when off (white text).
 */
@Composable
internal fun GlassPill(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showsState: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    ControlSurface(modifier, androidx.compose.foundation.shape.RoundedCornerShape(50), title, active, onClick, showsState, onLongClick) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (active) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (active) accent else Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, color = if (active) DarkText else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, color = if (active) GrayText else Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
