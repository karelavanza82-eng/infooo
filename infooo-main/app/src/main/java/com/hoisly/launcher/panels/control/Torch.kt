package com.hoisly.launcher.panels.control

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** The camera flash used as a flashlight. Needs no permission. [on] follows the real state. */
@Stable
class Torch internal constructor(private val manager: CameraManager?, private val cameraId: String?) {
    var on: Boolean by mutableStateOf(false)
        internal set

    val available: Boolean get() = manager != null && cameraId != null

    /** Switches the flash; false when the device has none or the camera is busy. */
    fun toggle(): Boolean {
        val id = cameraId ?: return false
        return runCatching { manager?.setTorchMode(id, !on) }.isSuccess
    }

    internal val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }
}

private fun flashCameraId(manager: CameraManager): String? = runCatching {
    manager.cameraIdList.firstOrNull { id ->
        val c = manager.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
}.getOrNull()

/** A [Torch] that listens to Android only while the composable is shown. */
@Composable
fun rememberTorch(): Torch {
    val context = LocalContext.current
    val torch = remember {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        Torch(manager, manager?.let(::flashCameraId))
    }
    DisposableEffect(torch) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (torch.available) manager?.registerTorchCallback(ContextCompat.getMainExecutor(context), torch.callback)
        onDispose { if (torch.available) manager?.unregisterTorchCallback(torch.callback) }
    }
    return torch
}
