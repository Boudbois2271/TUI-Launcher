package com.ilanmarchand.tui_launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String
)

// Every app with a launcher icon, except this one
fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            LaunchableApp(
                label = info.loadLabel(packageManager).toString(),
                packageName = info.activityInfo.packageName,
                activityName = info.activityInfo.name
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
