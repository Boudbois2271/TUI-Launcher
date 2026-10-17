package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.Workspace
import java.io.File

// GNU mv: mv SOURCE... DEST  Into DEST if it is a folder, else renames. The command log can't be moved.
object MvCommand : BuiltinCommand {
    override val name = "mv"
    override val synopsis = "mv SOURCE ... DEST"
    override val summary = "Move (rename) files."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("mv: unexpected EOF while looking for matching quote")
        return mv(words, CurrentWorkspace.get(context))
    }
}

// GNU cp: cp [-r] SOURCE... DEST  -r copies folders with what they hold.
object CpCommand : BuiltinCommand {
    override val name = "cp"
    override val synopsis = "cp [-r] SOURCE ... DEST"
    override val summary = "Copy files and directories."
    override val takesArgs = true

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("cp: unexpected EOF while looking for matching quote")
        return cp(words, CurrentWorkspace.get(context))
    }
}

// The sources and the destination, or GNU's message about missing operands or a destination that isn't a folder
private sealed interface Targets {
    data class Ok(val sources: List<String>, val destination: String, val destinationFile: File) : Targets
    data class Bad(val output: CommandResult) : Targets
}

private fun targets(tool: String, operands: List<String>, workspace: Workspace): Targets {
    if (operands.isEmpty()) return Targets.Bad(errorLines("$tool: missing file operand", tryHelp(tool)))
    if (operands.size == 1) {
        return Targets.Bad(errorLines("$tool: missing destination file operand after '${operands[0]}'", tryHelp(tool)))
    }
    val destination = operands.last()
    val destinationFile = workspace.resolve(destination)
    if (operands.size > 2 && !destinationFile.isDirectory) {
        return Targets.Bad(errorLines("$tool: target '$destination' is not a directory"))
    }
    return Targets.Ok(operands.dropLast(1), destination, destinationFile)
}

internal fun mv(words: List<String>, workspace: Workspace): CommandResult {
    val args = when (val result = parseGnuArgs("mv", words, "f", long = mapOf("force" to 'f'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("mv"))
        is GnuArgsResult.Parsed -> result.args
    }
    val targets = when (val result = targets("mv", args.operands, workspace)) {
        is Targets.Bad -> return result.output
        is Targets.Ok -> result
    }
    val errors = mutableListOf<OutputLine>()
    for (source in targets.sources) {
        val from = workspace.resolve(source)
        val intoFolder = targets.destinationFile.isDirectory
        val to = if (intoFolder) File(targets.destinationFile, from.name) else targets.destinationFile
        val shownTo = if (intoFolder) "${targets.destination.trimEnd('/')}/${from.name}" else targets.destination
        val reason = when {
            !from.exists() -> "cannot stat '$source': No such file or directory"
            workspace.holdsProtected(from) -> "cannot move '$source' to '$shownTo': Operation not permitted"
            from.renameTo(to) -> null
            // Another file system: copy, then remove the original
            runCatching { from.copyRecursively(to, overwrite = true) && from.deleteRecursively() }.getOrDefault(false) -> null
            else -> "cannot move '$source' to '$shownTo': Permission denied"
        }
        reason?.let { errors += OutputLine("mv: $it", isError = true) }
    }
    return CommandResult.Output(errors)
}

internal fun cp(words: List<String>, workspace: Workspace): CommandResult {
    val args = when (val result = parseGnuArgs("cp", words, "rRf", long = mapOf("recursive" to 'r', "force" to 'f'))) {
        is GnuArgsResult.Invalid -> return errorLines(result.message, tryHelp("cp"))
        is GnuArgsResult.Parsed -> result.args
    }
    val targets = when (val result = targets("cp", args.operands, workspace)) {
        is Targets.Bad -> return result.output
        is Targets.Ok -> result
    }
    val recursive = 'r' in args.flags || 'R' in args.flags
    val errors = mutableListOf<OutputLine>()
    for (source in targets.sources) {
        val from = workspace.resolve(source)
        val intoFolder = targets.destinationFile.isDirectory
        val to = if (intoFolder) File(targets.destinationFile, from.name) else targets.destinationFile
        val shownTo = if (intoFolder) "${targets.destination.trimEnd('/')}/${from.name}" else targets.destination
        val reason = when {
            !from.exists() -> "cannot stat '$source': No such file or directory"
            from.isDirectory && !recursive -> "-r not specified; omitting directory '$source'"
            workspace.isProtected(to) -> "cannot create regular file '$shownTo': Operation not permitted"
            to.parentFile?.isDirectory != true -> "cannot create regular file '$shownTo': No such file or directory"
            runCatching { if (from.isDirectory) from.copyRecursively(to, overwrite = true) else from.copyTo(to, overwrite = true).exists() }
                .getOrDefault(false) -> null
            else -> "cannot create regular file '$shownTo': Permission denied"
        }
        reason?.let { errors += OutputLine("cp: $it", isError = true) }
    }
    return CommandResult.Output(errors)
}
