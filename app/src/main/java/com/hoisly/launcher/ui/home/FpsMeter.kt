package com.hoisly.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos

/**
 * UI Lab: a small readout of frames per second and the slowest frame of the last half second,
 * for judging how smooth animations and scrolling feel. It never intercepts touches. Because the
 * meter itself asks for a frame every refresh, an idle screen reads the display's refresh rate;
 * what matters is how far the number drops while something moves.
 */
@Composable
fun FpsMeter() {
    var fps by remember { mutableIntStateOf(0) }
    var worstMs by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var windowStart = 0L
        var last = 0L
        var frames = 0
        var worst = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (windowStart == 0L) windowStart = now
            if (last != 0L) worst = maxOf(worst, now - last)
            last = now
            frames++
            val elapsed = now - windowStart
            if (elapsed >= 500_000_000L) {
                fps = (frames * 1_000_000_000L / elapsed).toInt()
                worstMs = worst / 1_000_000f
                windowStart = now
                frames = 0
                worst = 0
            }
        }
    }
    val color = when {
        fps >= 55 -> Color(0xFF7CFFB2)
        fps >= 40 -> Color(0xFFFFE066)
        else -> Color(0xFFFF7B72)
    }
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopEnd) {
        Text(
            text = "$fps FPS · ${"%.0f".format(worstMs)} ms",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 6.dp, end = 10.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
