package com.ilanmarchand.tui_launcher.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import com.ilanmarchand.tui_launcher.admin.AdminReceiver
import kotlin.math.roundToInt

// brightnessctl, for the screen brightness:
//   brightnessctl [-q] [-p] [info | get | max | set VALUE]   (also i, g, m, s)
// VALUE is 500, 50%, +10, 50-, +10%, 50%- or 10%+, as in brightnessctl. Like brightnessctl, an unknown
// operation shows the info. Brightness is Android's screen_brightness setting, written through the device
// owner's setSystemSetting, so no "Modify system settings" permission is needed. Texts follow brightnessctl.
object BrightnessctlCommand : BuiltinCommand {
    override val name = "brightnessctl"
    override val takesArgs = true

    // MediaTek phones name their screen backlight this way in /sys/class/leds
    private const val DEVICE = "Device 'lcd-backlight' of class 'leds':"

    private val HELP = listOf(
        "brightnessctl 0.5 - read and control device brightness.",
        "",
        "Usage: brightnessctl [options] [operation] [value]",
        "",
        "Options:",
        "  -l, --list\t\t\tlist devices with available brightness controls.",
        "  -q, --quiet\t\t\tsuppress output.",
        "  -p, --pretend\t\t\tdo not perform write operations.",
        "  -m, --machine-readable\tproduce machine-readable output.",
        "  -n, --min-value\t\tset minimum brightness, defaults to 1.",
        "  -e, --exponent[=K]\t\tchanges percentage curve to exponential.",
        "  -s, --save\t\t\tsave previous state in a temporary file.",
        "  -r, --restore\t\t\trestore previous saved state.",
        "  -h, --help\t\t\tprint this help.",
        "  -d, --device=DEVICE\t\tspecify device name (can be a wildcard).",
        "  -c, --class=CLASS\t\tspecify device class.",
        "  -V, --version\t\t\tprint version and exit.",
        "",
        "Operations:",
        "  i, info\t\t\tget device info.",
        "  g, get\t\t\tget current brightness of the device.",
        "  m, max\t\t\tget maximum brightness of the device.",
        "  s, set VALUE\t\t\tset brightness of the device.",
        "",
        "Valid values:",
        "  specific value\t\tExample: 500",
        "  percentage value\t\tExample: 50%",
        "  specific delta\t\tExample: 50- or +10",
        "  percentage delta\t\tExample: 50%- or +10%"
    )

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args)
            ?: return error("brightnessctl: unexpected EOF while looking for matching quote")
        var quiet = false
        var pretend = false
        val positional = mutableListOf<String>()
        for (word in words) {
            when (word) {
                "-q", "--quiet" -> quiet = true
                "-p", "--pretend" -> pretend = true
                "-h", "--help" -> return lines(HELP)
                else -> if (word.startsWith("-") && word.length > 1 && !word.drop(1).first().isDigit()) {
                    return error("'brightnessctl $word' is not available on this phone.")
                } else {
                    positional += word
                }
            }
        }
        val resolver = context.contentResolver
        val max = maxBrightness()
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, max)
        return when (positional.firstOrNull()) {
            "g", "get" -> lines(listOf(current.toString()))
            "m", "max" -> lines(listOf(max.toString()))
            "s", "set" -> {
                val value = positional.getOrNull(1) ?: return error("You need to provide a value to set.")
                val target = brightnessTarget(value, current, max) ?: return error("Invalid value given")
                if (!pretend) {
                    val dpm = context.getSystemService(DevicePolicyManager::class.java)
                    try {
                        dpm.setSystemSetting(
                            AdminReceiver.component(context), Settings.System.SCREEN_BRIGHTNESS, target.toString()
                        )
                    } catch (e: SecurityException) {
                        return error("Can't modify brightness: Permission denied")
                    }
                }
                if (quiet) lines(emptyList()) else lines(listOf("Updated device 'lcd-backlight':") + info(target, max))
            }
            // brightnessctl shows the info for info, for no operation and for an unknown one
            else -> if (quiet) lines(emptyList()) else lines(info(current, max))
        }
    }

    private fun info(current: Int, max: Int) = listOf(
        DEVICE,
        "\tCurrent brightness: $current (${(current * 100.0 / max).roundToInt()}%)",
        "\tMax brightness: $max"
    )

    // The top of Android's brightness setting; MIUI raises it above the stock 255
    private fun maxBrightness(): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        return if (id != 0) resources.getInteger(id) else 255
    }

    private fun lines(text: List<String>) = CommandResult.Output(text.map { OutputLine(it) })

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}

// brightnessctl's VALUE: 500, 50%, +10, 50-, +10%, 50%-, 10%+. The result stays between min and max, and
// brightnessctl's default minimum is 1. Null when VALUE isn't valid.
internal fun brightnessTarget(value: String, current: Int, max: Int, min: Int = 1): Int? {
    val match = Regex("""^(\+)?(\d+)(%)?([+-])?$""").matchEntire(value) ?: return null
    val (plus, number, percent, suffix) = match.destructured
    if (plus.isNotEmpty() && suffix.isNotEmpty()) return null
    val amount = number.toLongOrNull() ?: return null
    val raw = if (percent.isNotEmpty()) (amount * max / 100.0).roundToInt().toLong() else amount
    val target = when {
        plus.isNotEmpty() || suffix == "+" -> current + raw
        suffix == "-" -> current - raw
        else -> raw
    }
    return target.coerceIn(min.toLong(), max.toLong()).toInt()
}
