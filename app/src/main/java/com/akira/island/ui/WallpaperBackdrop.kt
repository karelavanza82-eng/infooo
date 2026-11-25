package com.akira.island.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hoisly.launcher.ui.glass.GlassSource
import com.hoisly.launcher.ui.glass.WallpaperBlur
import kotlin.math.roundToInt

/**
 * Latar halaman Dynamic Island yang transparan mengikuti wallpaper, sama seperti pengaturan Home:
 * wallpaper yang diburamkan + lapisan gelap tipis. Jika wallpaper tidak bisa dibaca (izin "Semua file"
 * belum diberikan atau live wallpaper), tidak ada yang digambar di bawah lapisan gelap sehingga
 * wallpaper asli tetap terlihat lewat jendela yang transparan (windowShowWallpaper).
 */
@Composable
fun Modifier.wallpaperBackdrop(): Modifier {
    val context = LocalContext.current.applicationContext
    var glass by remember { mutableStateOf<GlassSource?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumes) {
        glass = runCatching { WallpaperBlur.load(context) }.getOrNull()
    }
    val source = glass
    return drawBehind {
        if (source != null) {
            drawImage(
                image = source.image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
        drawRect(Ui.Scrim)
    }
}
