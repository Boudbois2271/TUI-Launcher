// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.system.freeHumanSize
import com.ilanmarchand.tui_launcher.system.readMemInfo

// procps free: free [-b|-k|-m|-g|-h]  Memory and swap from /proc/meminfo, in KiB by default. Like procps 4,
// "used" is total minus available.
object FreeCommand : BuiltinCommand {
    override val name = "free"
    override val synopsis = "free [-b | -k | -m | -g | -h]"
    override val summary = "Display amount of free and used memory in the system."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("free: unexpected EOF while looking for matching quote")
        return free(words, readMemInfo())
    }
}

private val FREE_LONG = mapOf("bytes" to 'b', "kibi" to 'k', "mebi" to 'm', "gibi" to 'g', "human" to 'h')

// mem: /proc/meminfo values in kB
internal fun free(words: List<String>, mem: Map<String, Long>?): CommandResult {
    val usage = arrayOf("", "Usage:", " free [options]")
    val args = when (val result = parseGnuArgs("free", words, "bkmgh", long = FREE_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, *usage)
        is GnuArgsResult.Parsed -> result.args
    }
    if (args.operands.isNotEmpty()) return errorLines(*usage.drop(1).toTypedArray())
    if (mem == null) return errorLines("free: Unable to read /proc/meminfo")
    val unit = "hgmkb".firstOrNull { it in args.flags } ?: 'k'
    fun size(kb: Long): String = when (unit) {
        'b' -> (kb * 1024).toString()
        'm' -> (kb / 1024).toString()
        'g' -> (kb / 1024 / 1024).toString()
        'h' -> freeHumanSize(kb * 1024)
        else -> kb.toString()
    }
    val total = mem["MemTotal"] ?: 0
    val free = mem["MemFree"] ?: 0
    val buffCache = (mem["Buffers"] ?: 0) + (mem["Cached"] ?: 0) + (mem["SReclaimable"] ?: 0)
    val available = mem["MemAvailable"] ?: (free + buffCache)
    val swapTotal = mem["SwapTotal"] ?: 0
    val swapFree = mem["SwapFree"] ?: 0
    fun row(label: String, values: List<String>) = label.padEnd(8) + values.joinToString("") { it.padStart(12) }
    return textLines(
        listOf(
            row("", listOf("total", "used", "free", "shared", "buff/cache", "available")),
            row("Mem:", listOf(total, total - available, free, mem["Shmem"] ?: 0, buffCache, available).map(::size)),
            row("Swap:", listOf(swapTotal, swapTotal - swapFree, swapFree).map(::size))
        )
    )
}
