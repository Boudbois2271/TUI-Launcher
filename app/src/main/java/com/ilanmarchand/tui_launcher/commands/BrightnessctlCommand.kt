package com.ilanmarchand.tui_launcher.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import com.ilanmarchand.tui_launcher.admin.AdminReceiver
import com.ilanmarchand.tui_launcher.system.Torch
import kotlin.math.roundToInt

// brightnessctl: brightnessctl [-lqpn] [-d DEVICE] [info | get | max | set VALUE]   (also i, g, m, s)
// Two devices: lcd-backlight, the screen (Android's screen_brightness, written through the device owner's
// setSystemSetting, so no "Modify system settings" permission is needed), and white:flash, the camera flash
// as a torch, as Linux phones name their flash LED. VALUE is 500, 50%, +10, 50-, +10%, 50%- or 10%+.
// Like brightnessctl, an unknown operation shows the info. Texts follow brightnessctl 0.5.
object BrightnessctlCommand : BuiltinCommand {
    override val name = "brightnessctl"
    override val synopsis = "brightnessctl [-lqpn] [-d DEVICE] [info | get | max | set VALUE]"
    override val summary = "Read or change the screen brightness, or the flash (-d white:flash)."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args)
            ?: return errorLines("brightnessctl: unexpected EOF while looking for matching quote")
        return brightnessctl(words, devices(context))
    }

    private fun devices(context: Context): List<BrightnessDevice> {
        val backlight = BrightnessDevice(
            name = "lcd-backlight",
            max = maxBrightness(),
            read = { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0) },
            // A device owner may change system settings from Android 9 on
            write = { value ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runCatching {
                    context.getSystemService(DevicePolicyManager::class.java).setSystemSetting(
                        AdminReceiver.component(context), Settings.System.SCREEN_BRIGHTNESS, value.toString()
                    )
                }.isSuccess
            }
        )
        if (!Torch.isAvailable(context)) return listOf(backlight)
        val flash = BrightnessDevice(
            name = "white:flash",
            max = 1,
            read = { if (Torch.isOn(context) == true) 1 else 0 },
            write = { value -> Torch.set(context, value > 0) }
        )
        return listOf(backlight, flash)
    }

    // The top of Android's brightness setting; MIUI raises it above the stock 255
    private fun maxBrightness(): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        return if (id != 0) resources.getInteger(id) else 255
    }
}

// A brightness control. write returns false when Android refuses the change.
internal class BrightnessDevice(val name: String, val max: Int, val read: () -> Int, val write: (Int) -> Boolean)

private val BRIGHTNESSCTL_HELP = listOf(
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

private val BRIGHTNESSCTL_LONG = mapOf(
    "list" to 'l', "quiet" to 'q', "pretend" to 'p', "machine-readable" to 'm', "min-value" to 'n',
    "exponent" to 'e', "save" to 's', "restore" to 'r', "help" to 'h', "device" to 'd', "class" to 'c',
    "version" to 'V'
)

internal fun brightnessctl(words: List<String>, devices: List<BrightnessDevice>): CommandResult {
    val args = when (val result = parseGnuArgs("brightnessctl", words, "lqpmnesrhV", "dc", BRIGHTNESSCTL_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message)
        is GnuArgsResult.Parsed -> result.args
    }
    when {
        'h' in args.flags -> return textLines(BRIGHTNESSCTL_HELP)
        'V' in args.flags -> return textLines(listOf("0.5"))
    }
    args.flags.firstOrNull { it in "mesr" }?.let { return errorLines("'brightnessctl -$it' is not available on this phone.") }
    if ('l' in args.flags) {
        val blocks = devices.map { deviceInfo(it, it.read()) }
        return textLines(listOf("Available devices:") + blocks.flatMapIndexed { index, block -> if (index == 0) block else listOf("") + block })
    }
    val pattern = args.values['d']?.last() ?: devices.first().name
    val wanted = Regex(glob(pattern))
    val classOk = args.values['c']?.last()?.let { it == "leds" } ?: true
    val device = devices.firstOrNull { classOk && wanted.matches(it.name) }
        ?: return errorLines("Device '$pattern' not found.")
    val quiet = 'q' in args.flags
    val current = device.read()
    return when (args.operands.firstOrNull()) {
        "g", "get" -> textLines(listOf(current.toString()))
        "m", "max" -> textLines(listOf(device.max.toString()))
        "s", "set" -> {
            val value = args.operands.getOrNull(1) ?: return errorLines("You need to provide a value to set.")
            val min = if ('n' in args.flags) 1 else 0
            val target = brightnessTarget(value, current, device.max, min) ?: return errorLines("Invalid value given")
            if ('p' !in args.flags && !device.write(target)) return errorLines("Can't modify brightness: Permission denied")
            if (quiet) textLines(emptyList()) else textLines(listOf("Updated device '${device.name}':") + deviceInfo(device, target))
        }
        // brightnessctl shows the info for info, for no operation and for an unknown one
        else -> if (quiet) textLines(emptyList()) else textLines(deviceInfo(device, current))
    }
}

// A device name pattern where * and ? are wildcards
private fun glob(pattern: String): String =
    pattern.map { c ->
        when (c) {
            '*' -> ".*"
            '?' -> "."
            else -> Regex.escape(c.toString())
        }
    }.joinToString("")

private fun deviceInfo(device: BrightnessDevice, current: Int) = listOf(
    "Device '${device.name}' of class 'leds':",
    "\tCurrent brightness: $current (${(current * 100.0 / device.max).roundToInt()}%)",
    "\tMax brightness: ${device.max}"
)

// brightnessctl's VALUE: 500, 50%, +10, 50-, +10%, 50%-, 10%+. The result stays between min and max; min is
// 0, or 1 with -n. Null when VALUE isn't valid.
internal fun brightnessTarget(value: String, current: Int, max: Int, min: Int = 0): Int? {
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
