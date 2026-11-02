// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager

// As device owner, grants the launcher one of its runtime permissions without a prompt, then leaves it under
// the user's control, so it can still be revoked in Settings. True when the launcher has the permission.
fun grantOwnPermission(context: Context, permission: String): Boolean {
    if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    if (!dpm.isDeviceOwnerApp(context.packageName)) return false
    val admin = AdminReceiver.component(context)
    val granted = dpm.setPermissionGrantState(
        admin, context.packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
    )
    if (!granted) return false
    dpm.setPermissionGrantState(
        admin, context.packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
    )
    return true
}
