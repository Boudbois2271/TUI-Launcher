// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import android.os.BatteryManager
import com.ilanmarchand.tui_launcher.system.Battery
import com.ilanmarchand.tui_launcher.system.readBattery
import java.util.Locale

// acpi: acpi [-b] [-a] [-V]  The battery (-b, the default) and the charger (-a, "adapter"). Android gives
// the time left to charge but not to discharge, which acpi reports as missing rate information.
object AcpiCommand : BuiltinCommand {
    override val name = "acpi"
    override val synopsis = "acpi [-b] [-a] [-V]"
    override val summary = "Show battery status and other ACPI information."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("acpi: unexpected EOF while looking for matching quote")
        return acpi(words, readBattery(context))
    }
}

private val ACPI_LONG = mapOf("battery" to 'b', "ac-adapter" to 'a', "everything" to 'V')

internal fun acpi(words: List<String>, battery: Battery?): CommandResult {
    val args = when (val result = parseGnuArgs("acpi", words, "baVtcifks", long = ACPI_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("acpi"))
        is GnuArgsResult.Parsed -> result.args
    }
    args.flags.firstOrNull { it in "tcifks" }?.let { return errorLines("acpi: -$it is not available on this phone") }
    if (battery == null) return errorLines("No support for device type: power_supply")
    val shown = if ('V' in args.flags) setOf('b', 'a') else args.flags.ifEmpty { setOf('b') }
    val lines = mutableListOf<String>()
    if ('b' in shown) lines += acpiBatteryLine(battery.percent, battery.status, battery.chargeTimeRemainingMs)
    if ('a' in shown) lines += "Adapter 0: ${if (battery.plugged != 0) "on-line" else "off-line"}"
    return textLines(lines)
}

internal fun acpiBatteryLine(percent: Int, status: Int, chargeTimeRemainingMs: Long): String = when (status) {
    BatteryManager.BATTERY_STATUS_FULL -> "Battery 0: Full, $percent%"
    BatteryManager.BATTERY_STATUS_CHARGING -> if (chargeTimeRemainingMs > 0) {
        val seconds = chargeTimeRemainingMs / 1000
        String.format(Locale.ROOT, "Battery 0: Charging, %d%%, %02d:%02d:%02d until charged", percent, seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "Battery 0: Charging, $percent%, rate information unavailable"
    }
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Battery 0: Discharging, $percent%, rate information unavailable"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Battery 0: Not charging, $percent%"
    else -> "Battery 0: Unknown, $percent%"
}
