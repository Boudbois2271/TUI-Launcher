// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String
)

// LauncherApps is the launcher API: one call returns every launchable activity with its label.
// Resolving each label through PackageManager took about 2 s on the test phone; this takes ~25 ms.
// Every app with a launcher icon, except this one
fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return launcherApps.getActivityList(null, Process.myUserHandle())
        .filter { it.componentName.packageName != context.packageName }
        .map { info ->
            LaunchableApp(
                label = info.label.toString(),
                packageName = info.componentName.packageName,
                activityName = info.componentName.className
            )
        }
}

// Until aliases exist, a name matches an app's label ignoring case
fun List<LaunchableApp>.findByName(name: String): LaunchableApp? =
    firstOrNull { it.label.equals(name, ignoreCase = true) }

fun LaunchableApp.launch(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(packageName, activityName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
