// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

// The admin component that lets the launcher become device owner, set from the PC with
// `adb shell dpm set-device-owner com.ilanmarchand.tui_launcher/.admin.AdminReceiver`.
// The system talks to it; it has no behavior of its own.
class AdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }
}
