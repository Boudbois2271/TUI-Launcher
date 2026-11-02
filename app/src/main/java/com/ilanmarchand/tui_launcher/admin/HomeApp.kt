// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.ilanmarchand.tui_launcher.MainActivity

// As device owner, makes this launcher the home app for good. A persistent preferred activity wins over
// the default-app setting, so MIUI, the home picker or an update can't switch home to another launcher.
// It survives reboots; calling it again is harmless. Without device owner it does nothing.
fun keepAsHomeApp(context: Context) {
    val dpm = context.getSystemService(DevicePolicyManager::class.java)
    if (!dpm.isDeviceOwnerApp(context.packageName)) return
    val home = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    dpm.addPersistentPreferredActivity(
        AdminReceiver.component(context),
        home,
        ComponentName(context, MainActivity::class.java)
    )
}
