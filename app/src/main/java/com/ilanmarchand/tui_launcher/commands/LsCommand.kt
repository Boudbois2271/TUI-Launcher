// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace
import com.ilanmarchand.tui_launcher.system.dfHumanSize
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

// GNU ls: ls [-aAlhd1] [FILE...]  One name per line (the phone is too narrow for columns), directories in
// the accent color as ls --color shows them. -l gives the long format; its "total" counts 4 KiB blocks,
// since Android doesn't tell an app how many blocks a file really takes.
object LsCommand : BuiltinCommand {
    override val name = "ls"
    override val synopsis = "ls [-aAlhd1] [FILE ...]"
    override val summary = "List directory contents."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("ls: unexpected EOF while looking for matching quote")
        return ls(words, CurrentWorkspace.get(context), System.currentTimeMillis())
    }
}

private val LS_LONG = mapOf("all" to 'a', "almost-all" to 'A', "human-readable" to 'h', "directory" to 'd')

private val byName: Comparator<Pair<String, File>> =
    Collator.getInstance(Locale.ROOT).let { collator -> Comparator { a, b -> collator.compare(a.first, b.first) } }

internal fun ls(words: List<String>, workspace: Workspace, now: Long): CommandResult {
    val args = when (val result = parseGnuArgs("ls", words, "aAlhd1", long = LS_LONG)) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("ls"), exitCode = 2)
        is GnuArgsResult.Parsed -> result.args
    }
    val long = 'l' in args.flags
    val human = 'h' in args.flags
    val operands = args.operands.ifEmpty { listOf(".") }
    val errors = mutableListOf<OutputLine>()
    val files = mutableListOf<Pair<String, File>>()
    val dirs = mutableListOf<Pair<String, File>>()
    for (operand in operands) {
        val file = workspace.resolve(operand)
        when {
            !file.exists() -> errors += OutputLine("ls: cannot access '$operand': No such file or directory", isError = true)
            file.isDirectory && 'd' !in args.flags -> dirs += operand to file
            else -> files += operand to file
        }
    }
    val out = mutableListOf<OutputLine>()
    out += entryLines(files.sortedWith(byName), long, human, now)
    for ((name, dir) in dirs.sortedWith(byName)) {
        if (operands.size > 1) {
            if (out.isNotEmpty()) out += OutputLine("")
            out += OutputLine("$name:")
        }
        val children = dir.listFiles()
        if (children == null) {
            errors += OutputLine("ls: cannot open directory '$name': Permission denied", isError = true)
            continue
        }
        val showHidden = 'a' in args.flags || 'A' in args.flags
        var entries = children.filter { showHidden || !it.name.startsWith(".") }.map { it.name to it }.sortedWith(byName)
        if ('a' in args.flags) entries = listOf("." to dir, ".." to (dir.parentFile ?: dir)) + entries
        if (long) {
            val kib = entries.sumOf { (_, file) -> if (file.isDirectory) 4L else ceil(file.length() / 4096.0).toLong() * 4 }
            out += OutputLine("total ${if (human) dfHumanSize(kib * 1024) else kib.toString()}")
        }
        out += entryLines(entries, long, human, now)
    }
    return CommandResult.Output(errors + out, if (errors.isEmpty()) 0 else 2)
}

private fun entryLines(entries: List<Pair<String, File>>, long: Boolean, human: Boolean, now: Long): List<OutputLine> {
    if (!long) return entries.map { (name, file) -> OutputLine(name, isDirectory = file.isDirectory) }
    val rows = entries.map { (name, file) ->
        listOf(mode(file), links(file), owner(file), group(file), if (human) dfHumanSize(file.length()) else file.length().toString(), date(file.lastModified(), now), name)
    }
    val widths = (1..4).associateWith { column -> rows.maxOfOrNull { it[column].length } ?: 0 }
    return rows.map { row ->
        OutputLine(
            listOf(
                row[0], row[1].padStart(widths.getValue(1)), row[2].padEnd(widths.getValue(2)), row[3].padEnd(widths.getValue(3)),
                row[4].padStart(widths.getValue(4)), row[5], row[6]
            ).joinToString(" ")
        )
    }
}

// "drwxr-xr-x": the type, then the permissions as Android reports them
private fun mode(file: File): String {
    val path = file.toPath()
    val type = when {
        Files.isSymbolicLink(path) -> 'l'
        file.isDirectory -> 'd'
        else -> '-'
    }
    val permissions = runCatching {
        PosixFilePermissions.toString(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))
    }.getOrElse {
        "${if (file.canRead()) 'r' else '-'}${if (file.canWrite()) 'w' else '-'}${if (file.canExecute()) 'x' else '-'}------"
    }
    return "$type$permissions"
}

private fun links(file: File) =
    runCatching { Files.getAttribute(file.toPath(), "unix:nlink", LinkOption.NOFOLLOW_LINKS).toString() }.getOrDefault("1")

private fun owner(file: File) =
    runCatching { Files.getOwner(file.toPath(), LinkOption.NOFOLLOW_LINKS).name }.getOrDefault("?")

private fun group(file: File) = runCatching {
    Files.readAttributes(file.toPath(), PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).group().name
}.getOrDefault("?")

// "Oct 17 15:23", or "Oct 17  2025" for a time over six months away, like GNU ls
private fun date(millis: Long, now: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val month = time.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val recent = abs(now - millis) < 15_778_476_000L
    val clock = if (recent) String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute) else " ${time.year}"
    return String.format(Locale.ROOT, "%s %2d %s", month, time.dayOfMonth, clock)
}
