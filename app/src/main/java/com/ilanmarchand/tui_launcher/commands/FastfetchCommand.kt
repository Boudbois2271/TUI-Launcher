package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import com.ilanmarchand.tui_launcher.apps.loadLaunchableApps
import com.ilanmarchand.tui_launcher.system.HOST_NAME
import com.ilanmarchand.tui_launcher.system.USER_NAME
import com.ilanmarchand.tui_launcher.system.displayDescription
import com.ilanmarchand.tui_launcher.system.fastfetchSize
import com.ilanmarchand.tui_launcher.system.kernelRelease
import com.ilanmarchand.tui_launcher.system.machine
import com.ilanmarchand.tui_launcher.system.maxCpuFrequencyKhz
import com.ilanmarchand.tui_launcher.system.mountOf
import com.ilanmarchand.tui_launcher.system.readBattery
import com.ilanmarchand.tui_launcher.system.readMemInfo
import com.ilanmarchand.tui_launcher.system.readMounts
import com.ilanmarchand.tui_launcher.system.storageOf
import com.ilanmarchand.tui_launcher.system.uptimeSeconds
import com.ilanmarchand.tui_launcher.system.uptimeShort
import java.util.Locale
import kotlin.math.roundToInt

// fastfetch, with its default modules and its small Android logo, placed above the information since the
// two side by side don't fit the phone. The logo and the keys use the accent color.
object FastfetchCommand : BuiltinCommand {
    override val name = "fastfetch"
    override val synopsis = "fastfetch"
    override val summary = "Show system information under the Android logo."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("fastfetch: unexpected EOF while looking for matching quote")
        if (words.isNotEmpty()) return errorLines("fastfetch: options are not available on this phone")
        return CommandResult.Output(fastfetchLines("$USER_NAME@$HOST_NAME", fields(context)))
    }

    private fun fields(context: Context): List<Pair<String, String>> {
        val fields = mutableListOf<Pair<String, String>>()
        fields += "OS" to "Android ${Build.VERSION.RELEASE} ${machine()}"
        fields += "Host" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        fields += "Kernel" to "Linux ${kernelRelease()}"
        fields += "Uptime" to uptimeShort(uptimeSeconds())
        fields += "Packages" to "${loadLaunchableApps(context).size} (apps)"
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        fields += "Shell" to listOfNotNull("TUI-Launcher", version).joinToString(" ")
        displayDescription(context)?.let { fields += "Display" to "$it [Built-in]" }
        fields += "Terminal" to "TUI-Launcher"
        val frequency = maxCpuFrequencyKhz()?.let { String.format(Locale.ROOT, " @ %.2f GHz", it / 1_000_000.0) }.orEmpty()
        fields += "CPU" to "${Build.HARDWARE.uppercase()} (${Runtime.getRuntime().availableProcessors()})$frequency"
        readMemInfo()?.let { mem ->
            val total = (mem["MemTotal"] ?: 0) * 1024
            val available = (mem["MemAvailable"] ?: 0) * 1024
            if (total > 0) fields += "Memory" to usage(total - available, total)
            val swapTotal = (mem["SwapTotal"] ?: 0) * 1024
            if (swapTotal > 0) fields += "Swap" to usage(swapTotal - (mem["SwapFree"] ?: 0) * 1024, swapTotal)
        }
        val data = Environment.getDataDirectory().path
        storageOf(data)?.let { storage ->
            val type = mountOf(data, readMounts())?.type?.let { " - $it" }.orEmpty()
            fields += "Disk ($data)" to usage(storage.total - storage.free, storage.total) + type
        }
        readBattery(context)?.let { battery ->
            val status = when (battery.status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }
            fields += "Battery" to "${battery.percent}% [$status]"
        }
        fields += "Locale" to Locale.getDefault().toString()
        return fields
    }

    // fastfetch's "7.06 GiB / 15.52 GiB (45%)"
    private fun usage(used: Long, total: Long) =
        "${fastfetchSize(used)} / ${fastfetchSize(total)} (${(used * 100.0 / total).roundToInt()}%)"
}

// fastfetch's android_small logo
private val ANDROID_LOGO = listOf(
    "  ;,           ,;",
    "   ';,.-----.,;'",
    "  ,'           ',",
    " /    O     O    \\",
    "|                 |",
    "'-----------------'"
)

internal fun fastfetchLines(title: String, fields: List<Pair<String, String>>): List<OutputLine> =
    ANDROID_LOGO.map { OutputLine(it, accentLength = it.length) } +
        OutputLine("") +
        OutputLine(title, accentLength = title.length) +
        OutputLine("-".repeat(title.length)) +
        fields.map { (key, value) -> OutputLine("$key: $value", accentLength = key.length) }
