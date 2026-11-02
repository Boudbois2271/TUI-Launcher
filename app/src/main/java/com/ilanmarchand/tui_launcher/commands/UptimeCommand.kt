// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.system.readLoadAverage
import com.ilanmarchand.tui_launcher.system.uptimeLine
import com.ilanmarchand.tui_launcher.system.uptimePretty
import com.ilanmarchand.tui_launcher.system.uptimeSeconds
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// procps uptime: uptime [-p] [-s]  The time, how long the phone has been on, and the load average when
// Android lets it be read. -p says it in words, -s prints when it started.
object UptimeCommand : BuiltinCommand {
    override val name = "uptime"
    override val synopsis = "uptime [-p] [-s]"
    override val summary = "Tell how long the system has been running."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("uptime: unexpected EOF while looking for matching quote")
        return uptime(words, LocalDateTime.now(), uptimeSeconds(), readLoadAverage())
    }
}

internal fun uptime(words: List<String>, now: LocalDateTime, upSeconds: Long, load: List<Double>?): CommandResult {
    val usage = arrayOf("", "Usage:", " uptime [options]")
    val args = when (val result = parseGnuArgs("uptime", words, "ps", long = mapOf("pretty" to 'p', "since" to 's'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, *usage)
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isNotEmpty()) return errorLines(*usage.drop(1).toTypedArray())
    return textLines(
        listOf(
            when {
                's' in args.flags -> now.minusSeconds(upSeconds).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                'p' in args.flags -> uptimePretty(upSeconds)
                else -> uptimeLine(now.toLocalTime(), upSeconds, load)
            }
        )
    )
}
