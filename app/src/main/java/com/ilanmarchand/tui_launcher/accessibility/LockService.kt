package com.ilanmarchand.tui_launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

// An accessibility service for two of Android's system actions: locking the screen, the same as the power
// button (unlike the device admin lock, it doesn't make the next unlock ask for the PIN, so face unlock
// keeps working), and taking a screenshot, the same as power + volume down. It receives no events and
// can't read the screen. It must be turned on once in the Accessibility settings. The class keeps its first
// name, since renaming it would turn the service off.
class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        running = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        // Set while Android has the service connected, cleared when it unbinds
        @Volatile
        private var running: LockService? = null

        // Locks the screen like the power button. False when the service is off, or before Android 9.
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return running?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }

        // Takes a screenshot like power + volume down; Android saves it in Pictures/Screenshots.
        // False when the service is off, or before Android 9.
        fun takeScreenshot(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return running?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
        }
    }
}
