package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import android.os.Environment
import com.ilanmarchand.tui_launcher.system.dfHumanSize
import com.ilanmarchand.tui_launcher.system.mountOf
import com.ilanmarchand.tui_launcher.system.readMounts
import com.ilanmarchand.tui_launcher.system.storageOf
import java.io.File
import kotlin.math.ceil

// GNU df: df [-h] [FILE...]  Space on the file systems holding each FILE, by default the app data
// partition (/data) and the shared storage (/storage/emulated). Like df, -h sizes and Use% round up.
object DfCommand : BuiltinCommand {
    override val name = "df"
    override val synopsis = "df [-h] [FILE ...]"
    override val summary = "Report file system space usage."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("df: unexpected EOF while looking for matching quote")
        val parsed = when (val result = parseGnuArgs("df", words, "h", long = mapOf("human-readable" to 'h'))) {
            is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("df"))
            is GnuArgsResult.Parsed -> result.args
        }
        val paths = parsed.operands.ifEmpty {
            listOf(Environment.getDataDirectory().path, Environment.getExternalStorageDirectory().path)
        }
        val mounts = readMounts()
        val errors = mutableListOf<OutputLine>()
        val rows = mutableListOf<DfRow>()
        for (path in paths) {
            val storage = storageOf(path)
            if (!File(path).exists() || storage == null) {
                errors += OutputLine("df: $path: No such file or directory", isError = true)
                continue
            }
            val mount = mountOf(path, mounts)
            rows += DfRow(mount?.source ?: path, storage.total, storage.total - storage.free, storage.available, mount?.point ?: path)
        }
        val table = if (rows.isEmpty()) emptyList() else dfTable(rows.distinctBy { it.point }, 'h' in parsed.flags)
        return CommandResult.Output(errors + table.map { OutputLine(it) })
    }
}

// Sizes in bytes
internal data class DfRow(val source: String, val total: Long, val used: Long, val available: Long, val point: String)

// GNU df's table: columns sized to their widest cell (5 at least for -h sizes), separated by one space
internal fun dfTable(rows: List<DfRow>, human: Boolean): List<String> {
    fun size(bytes: Long) = if (human) dfHumanSize(bytes) else ceil(bytes / 1024.0).toLong().toString()
    fun percent(row: DfRow) =
        if (row.used + row.available == 0L) "-" else "${ceil(row.used * 100.0 / (row.used + row.available)).toLong()}%"
    val header = listOf("Filesystem", if (human) "Size" else "1K-blocks", "Used", if (human) "Avail" else "Available", "Use%", "Mounted on")
    val cells = rows.map { listOf(it.source, size(it.total), size(it.used), size(it.available), percent(it), it.point) }
    val all = listOf(header) + cells
    val widths = (0..4).map { column ->
        val widest = all.maxOf { it[column].length }
        if (human && column in 1..3) maxOf(widest, 5) else widest
    }
    return all.map { row ->
        listOf(row[0].padEnd(widths[0]), row[1].padStart(widths[1]), row[2].padStart(widths[2]), row[3].padStart(widths[3]), row[4].padStart(widths[4]), row[5])
            .joinToString(" ")
    }
}
