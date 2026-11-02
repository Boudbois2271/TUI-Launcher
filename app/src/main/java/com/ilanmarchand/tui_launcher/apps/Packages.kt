// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.apps

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// An installed package as pacman sees it: its Android package name, its version, its app name when it has
// an icon, and whether it is part of the system (which Android can't uninstall)
data class InstalledPackage(val name: String, val version: String, val label: String?, val isSystem: Boolean)

// Every installed package, sorted by name. App names come from LauncherApps, since asking PackageManager for
// each label is slow.
fun installedPackages(context: Context): List<InstalledPackage> {
    val labels = loadLaunchableApps(context).associate { it.packageName to it.label }
    @Suppress("DEPRECATION")
    val infos = context.packageManager.getInstalledPackages(0)
    return infos.map { info ->
        InstalledPackage(
            name = info.packageName,
            version = info.versionName ?: "unknown",
            label = labels[info.packageName],
            isSystem = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
        )
    }.sortedBy { it.name }
}

// Uninstalls a package. As device owner Android does it without asking; otherwise it shows its confirmation.
// Waits up to a minute for the result. Returns null on success, else Android's reason.
fun uninstallPackage(context: Context, packageName: String): String? {
    val action = "${context.packageName}.UNINSTALL_RESULT.${System.nanoTime()}"
    val done = CountDownLatch(1)
    var status = PackageInstaller.STATUS_FAILURE
    var message: String? = null
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // Not device owner: Android asks the user, then sends the final result
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return
            }
            done.countDown()
        }
    }
    val filter = IntentFilter(action)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
    try {
        // The installer adds the result to the intent, so it must stay mutable
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or mutable
        )
        context.packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
        if (!done.await(60, TimeUnit.SECONDS)) return "no answer from Android"
    } catch (e: Exception) {
        return e.message ?: e.toString()
    } finally {
        context.unregisterReceiver(receiver)
    }
    return if (status == PackageInstaller.STATUS_SUCCESS) null else message ?: "unknown error"
}

// Opens a Play Store page, such as market://details?id=… False when no app can open it.
fun openPlayStore(context: Context, uri: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}
