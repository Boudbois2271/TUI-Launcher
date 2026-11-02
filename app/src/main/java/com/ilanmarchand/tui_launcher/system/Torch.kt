// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The camera flash used as a torch, which Linux phones expose as the white:flash LED. Android reports the
// torch state through a callback, so the first read waits briefly for it. No permission is needed.
object Torch {
    @Volatile
    private var on = false
    private var watching = false
    private val firstState = CountDownLatch(1)

    private fun flashCamera(manager: CameraManager): String? = runCatching {
        manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    @Synchronized
    private fun watch(manager: CameraManager, camera: String) {
        if (watching) return
        watching = true
        manager.registerTorchCallback(
            object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == camera) {
                        on = enabled
                        firstState.countDown()
                    }
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    fun isAvailable(context: Context): Boolean =
        flashCamera(context.getSystemService(CameraManager::class.java)) != null

    // Whether the torch is on, or null when the phone has no flash. Call it off the main thread.
    fun isOn(context: Context): Boolean? {
        val manager = context.getSystemService(CameraManager::class.java)
        val camera = flashCamera(manager) ?: return null
        watch(manager, camera)
        firstState.await(500, TimeUnit.MILLISECONDS)
        return on
    }

    // False when the phone has no flash or the camera is busy
    fun set(context: Context, enabled: Boolean): Boolean {
        val manager = context.getSystemService(CameraManager::class.java)
        val camera = flashCamera(manager) ?: return false
        return runCatching { manager.setTorchMode(camera, enabled) }.isSuccess.also { if (it) on = enabled }
    }
}
