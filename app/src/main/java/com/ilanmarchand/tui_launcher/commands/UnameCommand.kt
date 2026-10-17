package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.system.HOST_NAME
import com.ilanmarchand.tui_launcher.system.kernelRelease
import com.ilanmarchand.tui_launcher.system.kernelVersion
import com.ilanmarchand.tui_launcher.system.machine

// GNU uname: uname [-asnrvmopi]  Android's Linux kernel, with the prompt's host name and Android as the
// operating system (GNU/Linux on a desktop).
object UnameCommand : BuiltinCommand {
    override val name = "uname"
    override val synopsis = "uname [-asnrvmo]"
    override val summary = "Print system information."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("uname: unexpected EOF while looking for matching quote")
        return uname(words, UnameInfo(kernelRelease(), kernelVersion() ?: "unknown", machine()))
    }
}

internal data class UnameInfo(val release: String, val version: String, val machine: String)

private val UNAME_LONG = mapOf(
    "all" to 'a', "kernel-name" to 's', "nodename" to 'n', "kernel-release" to 'r', "kernel-version" to 'v',
    "machine" to 'm', "processor" to 'p', "hardware-platform" to 'i', "operating-system" to 'o'
)

internal fun uname(words: List<String>, info: UnameInfo): CommandResult {
    val args = when (val result = parseGnuArgs("uname", words, "asnrvmpio", long = UNAME_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("uname"))
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isNotEmpty()) return errorLines("uname: extra operand ‘${args.operands.first()}’", tryHelp("uname"))
    val values = linkedMapOf(
        's' to "Linux", 'n' to HOST_NAME, 'r' to info.release, 'v' to info.version, 'm' to info.machine,
        'p' to "unknown", 'i' to "unknown", 'o' to "Android"
    )
    // Like GNU uname, -a leaves out the processor and hardware platform when they are unknown
    val shown = when {
        'a' in args.flags -> values.filterKeys { it !in "pi" }
        args.flags.isEmpty() -> values.filterKeys { it == 's' }
        else -> values.filterKeys { it in args.flags }
    }
    return textLines(listOf(shown.values.joinToString(" ")))
}
