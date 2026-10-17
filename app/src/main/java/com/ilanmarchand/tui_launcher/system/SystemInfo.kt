package com.ilanmarchand.tui_launcher.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import android.view.Display
import java.io.File

// Readings of the phone's state for the system commands. Android may refuse access to some files under
// /proc and /sys, so these readers return null (or an empty list) instead of failing.

fun uptimeSeconds(): Long = SystemClock.elapsedRealtime() / 1000

// /proc/meminfo, in kB, by name (MemTotal, MemAvailable, SwapFree…)
fun readMemInfo(): Map<String, Long>? = runCatching {
    File("/proc/meminfo").readLines().mapNotNull { line ->
        val value = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
        value?.let { line.substringBefore(':') to it }
    }.toMap()
}.getOrNull()

fun readLoadAverage(): List<Double>? = runCatching {
    File("/proc/loadavg").readText().trim().split(' ').take(3).map { it.toDouble() }
}.getOrNull()

fun kernelRelease(): String = System.getProperty("os.version") ?: "unknown"

// The kernel's build, such as "#1 SMP PREEMPT Wed Jun 1 12:00:00 CST 2022", from uname(2) like GNU uname
// (apps can't read /proc/version)
fun kernelVersion(): String? = runCatching { Os.uname().version }.getOrNull()?.takeIf { it.isNotEmpty() }

// The time zone's abbreviation now, such as CEST, as the C library names it. Java only knows names like
// GMT+02:00 for most zones, so the phone's own date prints it.
fun zoneAbbreviation(): String? = runCatching {
    val process = ProcessBuilder("/system/bin/date", "+%Z").redirectErrorStream(true).start()
    process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
}.getOrNull()?.takeIf { it.isNotEmpty() && ' ' !in it }

fun machine(): String = System.getProperty("os.arch") ?: "unknown"

data class Mount(val source: String, val point: String, val type: String)

fun readMounts(): List<Mount> = runCatching {
    File("/proc/self/mounts").readLines().mapNotNull { line ->
        val parts = line.split(' ')
        if (parts.size >= 3) Mount(parts[0], parts[1].replace("\\040", " "), parts[2]) else null
    }
}.getOrDefault(emptyList())

// The mount a path lives on: the longest mount point that contains it
fun mountOf(path: String, mounts: List<Mount>): Mount? = mounts
    .filter { it.point == "/" || path == it.point || path.startsWith(it.point.trimEnd('/') + "/") }
    .maxByOrNull { it.point.length }

data class Storage(val total: Long, val free: Long, val available: Long)

fun storageOf(path: String): Storage? = runCatching {
    StatFs(path).let { Storage(it.totalBytes, it.freeBytes, it.availableBytes) }
}.getOrNull()

data class Battery(val percent: Int, val status: Int, val plugged: Int, val chargeTimeRemainingMs: Long)

fun readBattery(context: Context): Battery? {
    val intent = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    if (level < 0 || scale <= 0) return null
    val chargeTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()
    } else {
        -1L
    }
    return Battery(
        percent = level * 100 / scale,
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        chargeTimeRemainingMs = chargeTime
    )
}

// "1080x2340, 60 Hz"
fun displayDescription(context: Context): String? = runCatching {
    val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val mode = display.mode
    "${mode.physicalWidth}x${mode.physicalHeight}, ${mode.refreshRate.toInt()} Hz"
}.getOrNull()

// The highest CPU frequency over all cores, in kHz
fun maxCpuFrequencyKhz(): Long? = runCatching {
    File("/sys/devices/system/cpu").listFiles { file -> file.name.matches(Regex("cpu\\d+")) }
        ?.mapNotNull { File(it, "cpufreq/cpuinfo_max_freq").takeIf(File::exists)?.readText()?.trim()?.toLongOrNull() }
        ?.maxOrNull()
}.getOrNull()
