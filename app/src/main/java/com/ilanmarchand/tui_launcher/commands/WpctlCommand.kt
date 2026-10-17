package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import android.media.AudioManager
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

// WirePlumber's wpctl, for the media volume. @DEFAULT_AUDIO_SINK@ (or @DEFAULT_SINK@) is the phone's media
// volume, which Android splits into steps (15 on the test phone), so a volume lands on the nearest step.
//   wpctl get-volume ID
//   wpctl set-volume [-l LIMIT] ID VOL[%][-/+]
//   wpctl set-mute ID 1|0|toggle
// Usage, help and error texts are copied from the real wpctl.
object WpctlCommand : BuiltinCommand {
    override val name = "wpctl"
    override val synopsis = "wpctl get-volume | set-volume [-l LIMIT] | set-mute @DEFAULT_AUDIO_SINK@ ..."
    override val summary = "Read or change the media volume, like WirePlumber's wpctl."
    override val takesArgs = true

    private const val STREAM = AudioManager.STREAM_MUSIC
    private val SINKS = listOf("@DEFAULT_AUDIO_SINK@", "@DEFAULT_SINK@")
    private val SOURCES = listOf("@DEFAULT_AUDIO_SOURCE@", "@DEFAULT_SOURCE@")
    private const val USAGE_LINE = "  wpctl [OPTION…] COMMAND [COMMAND_OPTIONS] - WirePlumber Control CLI"

    private val USAGE = listOf(
        "Usage:",
        USAGE_LINE,
        "",
        "Commands:",
        "  status ",
        "  list [audio|video] [devices|sinks|sources]",
        "  get-volume ID",
        "  inspect ID",
        "  set-default ID",
        "  set-volume ID VOL[%][-/+]",
        "  set-mute ID 1|0|toggle",
        "  set-profile ID INDEX",
        "  set-route ID INDEX",
        "  clear-default [ID]",
        "  settings [KEY] [VAL]",
        "  set-log-level [ID] LEVEL",
        "  reset ",
        "",
        "Help Options:",
        "  -h, --help       Show help options",
        "",
        "Pass -h after a command to see command-specific options"
    )

    private val COMMANDS = listOf(
        "status", "list", "get-volume", "inspect", "set-default", "set-volume", "set-mute", "set-profile",
        "set-route", "clear-default", "settings", "set-log-level", "reset"
    )

    // Command-specific help, as printed by `wpctl COMMAND -h`
    private val COMMAND_HELP = mapOf(
        "get-volume" to listOf(
            "Command: get-volume ID",
            "  Displays volume information about the specified node in PipeWire"
        ),
        "set-volume" to listOf(
            "Command: set-volume ID VOL[%][-/+]",
            "  Sets the volume of ID from specified argument. (floating point, 1.0 is 100%)",
            "  VOL%[-/+] - Step up/down volume by specified percent (Example: 0.5%+)",
            "  VOL[-/+] - Step up/down volume by specified value (Example: 0.5+)",
            "  VOL - Set volume as the specified value (Example: 0.5)",
            "",
            "Help Options:",
            "  -h, --help       Show help options",
            "",
            "Application Options:",
            "  -p, --pid        Selects all nodes associated to the given PID number",
            "  -l, --limit      Limits the final volume percentage to below this value. (floating point, 1.0 is 100%)"
        ),
        "set-mute" to listOf(
            "Command: set-mute ID 1|0|toggle",
            "  Changes the mute state of ID"
        )
    )

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return error("wpctl: unexpected EOF while looking for matching quote")
        val command = words.firstOrNull() ?: return lines(USAGE)
        if (command == "-h" || command == "--help") return lines(USAGE)
        if (command !in COMMANDS) return lines(USAGE, isError = true)
        val rest = words.drop(1)
        if ("-h" in rest || "--help" in rest) {
            val help = COMMAND_HELP[command] ?: return notAvailable(command)
            return lines(listOf("Usage:", USAGE_LINE, "") + help)
        }
        val audio = context.getSystemService(AudioManager::class.java)
        return when (command) {
            "get-volume" -> getVolume(audio, rest)
            "set-volume" -> setVolume(audio, rest)
            "set-mute" -> setMute(audio, rest)
            else -> notAvailable(command)
        }
    }

    private fun getVolume(audio: AudioManager, args: List<String>): CommandResult {
        val id = args.firstOrNull() ?: return usageError("get-volume", "Error: ID is required")
        nodeProblem(id)?.let { return it }
        val max = audio.getStreamMaxVolume(STREAM)
        val volume = audio.getStreamVolume(STREAM).toDouble() / max
        val muted = if (audio.isStreamMute(STREAM)) " [MUTED]" else ""
        return lines(listOf(String.format(Locale.ROOT, "Volume: %.2f%s", volume, muted)))
    }

    private fun setVolume(audio: AudioManager, args: List<String>): CommandResult {
        var limit: Double? = null
        val positional = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            val word = args[index++]
            when {
                word == "-l" || word == "--limit" -> {
                    val value = args.getOrNull(index++)
                    limit = value?.toDoubleOrNull() ?: return error("Error: invalid limit '${value.orEmpty()}'")
                }
                word.startsWith("--limit=") -> {
                    val value = word.removePrefix("--limit=")
                    limit = value.toDoubleOrNull() ?: return error("Error: invalid limit '$value'")
                }
                word == "-p" || word == "--pid" -> return error("'wpctl set-volume --pid' is not available on this phone.")
                else -> positional += word
            }
        }
        if (positional.size < 2) return usageError("set-volume", "Error: ID and VOL[%][-/+] are required")
        val (id, value) = positional
        nodeProblem(id)?.let { return it }
        val max = audio.getStreamMaxVolume(STREAM)
        val step = volumeStep(value, audio.getStreamVolume(STREAM), max, limit)
            ?: return error("Error: invalid volume '$value'")
        audio.setStreamVolume(STREAM, step, 0)
        return lines(emptyList())
    }

    private fun setMute(audio: AudioManager, args: List<String>): CommandResult {
        val required = "Error: ID and one of '1', '0' or 'toggle' are required"
        if (args.size < 2) return usageError("set-mute", required)
        val (id, state) = args
        nodeProblem(id)?.let { return it }
        val direction = when (state) {
            "1" -> AudioManager.ADJUST_MUTE
            "0" -> AudioManager.ADJUST_UNMUTE
            "toggle" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return usageError("set-mute", required)
        }
        audio.adjustStreamVolume(STREAM, direction, 0)
        return lines(emptyList())
    }

    // Only the default sink exists here: the media volume
    private fun nodeProblem(id: String): CommandResult? = when (id) {
        in SINKS -> null
        in SOURCES -> error("'$id' is not available on this phone.")
        else -> error("Node '$id' not found")
    }

    private fun usageError(command: String, message: String) = lines(
        listOf(message, "", "Usage:", USAGE_LINE, "") + COMMAND_HELP.getValue(command).take(2),
        isError = true
    )

    private fun notAvailable(command: String) = error("'wpctl $command' is not available on this phone.")

    private fun lines(text: List<String>, isError: Boolean = false) =
        CommandResult.Output(text.map { OutputLine(it, isError) })

    private fun error(message: String) = lines(listOf(message), isError = true)
}

// wpctl's VOL[%][-/+] applied to Android's volume steps (0..max). A relative change always moves at least
// one step, so `5%+` works even when 5% is smaller than a step. Null when VOL isn't valid.
internal fun volumeStep(value: String, current: Int, max: Int, limit: Double?): Int? {
    val match = Regex("""^(\d+(?:\.\d+)?|\.\d+)(%)?([+-])?$""").matchEntire(value) ?: return null
    val (number, percent, sign) = match.destructured
    val amount = number.toDouble() / if (percent.isEmpty()) 1.0 else 100.0
    val nearest = { volume: Double -> (volume * max).roundToInt() }
    var step = when {
        amount == 0.0 && sign.isNotEmpty() -> current
        sign == "+" -> maxOf(current + 1, nearest(current.toDouble() / max + amount))
        sign == "-" -> minOf(current - 1, nearest(current.toDouble() / max - amount))
        else -> nearest(amount)
    }
    if (limit != null) step = minOf(step, floor(limit * max + 1e-9).toInt())
    return step.coerceIn(0, max)
}
