// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.system

import java.time.LocalTime
import java.util.Locale
import kotlin.math.ceil

// procps uptime: " 15:01:09 up  3:19,  1 user,  load average: 0.11, 0.11, 0.07". The load average part is
// left out when Android doesn't let /proc/loadavg be read.
fun uptimeLine(now: LocalTime, upSeconds: Long, load: List<Double>?): String {
    val days = upSeconds / 86_400
    val hours = upSeconds % 86_400 / 3600
    val minutes = upSeconds % 3600 / 60
    val line = StringBuilder(String.format(Locale.ROOT, " %02d:%02d:%02d up ", now.hour, now.minute, now.second))
    if (days > 0) line.append("$days day${if (days != 1L) "s" else ""}, ")
    if (hours > 0) line.append(String.format(Locale.ROOT, "%2d:%02d, ", hours, minutes)) else line.append("$minutes min, ")
    line.append(" 1 user")
    if (load != null && load.size == 3) {
        line.append(String.format(Locale.ROOT, ",  load average: %.2f, %.2f, %.2f", load[0], load[1], load[2]))
    }
    return line.toString()
}

// uptime -p: "up 1 week, 2 days, 3 hours, 19 minutes"
fun uptimePretty(upSeconds: Long): String {
    val parts = listOf(
        upSeconds / 604_800 to "week",
        upSeconds % 604_800 / 86_400 to "day",
        upSeconds % 86_400 / 3600 to "hour",
        upSeconds % 3600 / 60 to "minute"
    ).filter { it.first > 0 }.map { (count, unit) -> "$count $unit${if (count != 1L) "s" else ""}" }
    return "up " + parts.ifEmpty { listOf("0 minutes") }.joinToString(", ")
}

// fastfetch: "2 days, 3 hours, 19 mins"
fun uptimeShort(upSeconds: Long): String {
    val parts = listOf(
        upSeconds / 86_400 to "day",
        upSeconds % 86_400 / 3600 to "hour",
        upSeconds % 3600 / 60 to "min"
    ).filter { it.first > 0 }.map { (count, unit) -> "$count $unit${if (count != 1L) "s" else ""}" }
    return parts.ifEmpty { listOf("0 mins") }.joinToString(", ")
}

// fastfetch sizes: "0 B", "544.37 MiB", "15.52 GiB"
fun fastfetchSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.ROOT, "%.2f %s", value, units[unit])
}

// procps free -h sizes: "0B", "544Mi", "7.1Gi", "15Gi" (below 10 with one decimal, above it cut down)
fun freeHumanSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val units = listOf("Ki", "Mi", "Gi", "Ti", "Pi")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value < 10) String.format(Locale.ROOT, "%.1f%s", value, units[unit]) else "${value.toLong()}${units[unit]}"
}

// GNU df -h sizes: "222G", "9.8G", rounded up as df does
fun dfHumanSize(bytes: Long): String {
    if (bytes < 1024) return bytes.toString()
    val units = "KMGTPE"
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value < 10) {
        String.format(Locale.ROOT, "%.1f%c", ceil(value * 10) / 10, units[unit])
    } else {
        "${ceil(value).toLong()}${units[unit]}"
    }
}
