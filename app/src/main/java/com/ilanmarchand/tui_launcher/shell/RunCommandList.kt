package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine

// What running a line does to the terminal, in order
sealed interface ShellEffect {
    data class Print(val line: OutputLine) : ShellEffect
    data object ClearScreen : ShellEffect

    // The next typed line answers the question instead of being run
    data class Ask(val question: String, val answer: (String) -> CommandResult) : ShellEffect

    // The nano editor takes the screen
    data class Edit(val edit: CommandResult.Edit) : ShellEffect
}

// Runs one command, already alias-expanded, with the output of the command before it in a pipeline
// (null for the first command)
fun interface CommandRunner {
    suspend fun run(command: String, stdin: List<String>?): CommandResult
}

// The files redirections read and write. A failure throws RedirectException with bash's message, such as
// "notes: Is a directory".
interface RedirectFiles {
    fun read(target: String): List<String>
    fun write(target: String, lines: List<String>, append: Boolean)
}

class RedirectException(message: String) : Exception(message)

// Expands a command's text (aliases, then variables) and a redirection's file name (variables) just before
// they run, given the exit status of the pipeline before ($?)
interface Expander {
    fun command(text: String, lastStatus: Int): String
    fun target(target: String, lastStatus: Int): String
}

// Runs a parsed command list like bash: after ; the next pipeline always runs, after && only if the last
// one succeeded, after || only if it failed. Each command is expanded just before it runs. Returns the exit
// status of the last pipeline that ran. A command that asks a question ends the line there: the answer comes
// with the next typed line, and the rest of the line is dropped.
suspend fun runCommandList(
    items: List<ListItem>,
    expand: Expander,
    runner: CommandRunner,
    files: RedirectFiles,
    emit: suspend (ShellEffect) -> Unit
): Int {
    var status = 0
    for (item in items) {
        val runs = when (item.connector) {
            null, Connector.THEN -> true
            Connector.AND -> status == 0
            Connector.OR -> status != 0
        }
        if (runs) status = runPipeline(item.pipeline, status, expand, runner, files, emit) ?: return status
    }
    return status
}

// Each command's output, without its error lines, is the next command's input. Error lines are printed
// as they come, and the last command's lines are printed in their own order. The status is the last
// command's, or null when a command asked a question.
private suspend fun runPipeline(
    pipeline: List<SimpleCommand>,
    lastStatus: Int,
    expand: Expander,
    runner: CommandRunner,
    files: RedirectFiles,
    emit: suspend (ShellEffect) -> Unit
): Int? {
    var stdin: List<String>? = null
    var status = 0
    for ((index, command) in pipeline.withIndex()) {
        val isLast = index == pipeline.lastIndex
        val redirects = command.redirects.map { it.copy(target = expand.target(it.target, lastStatus)) }
        val result = when (val prepared = prepareRedirects(redirects, files)) {
            // Like bash, a redirection that fails stops its command from running
            is Prepared.Failed -> prepared.output
            is Prepared.Ready -> {
                val input = prepared.input ?: stdin
                val text = expand.command(command.text, lastStatus)
                val ran = if (text.isBlank()) CommandResult.Output(emptyList()) else runner.run(text, input)
                if (ran is CommandResult.Ask) return showResult(ran, emit)
                writeRedirects(ran, redirects, files)
            }
        }
        if (isLast) return showResult(result, emit)
        when (result) {
            CommandResult.Clear -> {
                stdin = emptyList()
                status = 0
            }
            is CommandResult.Output -> {
                result.lines.filter { it.isError }.forEach { emit(ShellEffect.Print(it)) }
                stdin = result.lines.filterNot { it.isError }.map { it.text }
                status = result.exitCode
            }
            // A question or the editor only happens at the end of a pipeline
            is CommandResult.Ask, is CommandResult.Edit -> {
                stdin = emptyList()
                status = 0
            }
        }
    }
    return status
}

private sealed interface Prepared {
    // input: what < read, or null to keep the pipe's input
    data class Ready(val input: List<String>?) : Prepared
    data class Failed(val output: CommandResult) : Prepared
}

// Before the command runs, as bash does: < reads its file, > empties its file and >> makes sure its file
// exists, in the order typed
private fun prepareRedirects(redirects: List<Redirect>, files: RedirectFiles): Prepared {
    var input: List<String>? = null
    try {
        for (redirect in redirects) {
            when (redirect.kind) {
                RedirectKind.READ -> input = files.read(redirect.target)
                RedirectKind.WRITE, RedirectKind.ERRORS_WRITE -> files.write(redirect.target, emptyList(), append = false)
                RedirectKind.APPEND, RedirectKind.ERRORS_APPEND -> files.write(redirect.target, emptyList(), append = true)
            }
        }
    } catch (e: RedirectException) {
        return Prepared.Failed(CommandResult.Output(listOf(OutputLine(e.message.orEmpty(), isError = true))))
    }
    return Prepared.Ready(input)
}

// After the command runs: its output goes to the last > or >> file and its error lines to the last 2> or
// 2>> file, instead of the screen or the next command
private fun writeRedirects(result: CommandResult, redirects: List<Redirect>, files: RedirectFiles): CommandResult {
    val output = redirects.lastOrNull { it.kind == RedirectKind.WRITE || it.kind == RedirectKind.APPEND }
    val errors = redirects.lastOrNull { it.kind == RedirectKind.ERRORS_WRITE || it.kind == RedirectKind.ERRORS_APPEND }
    if (output == null && errors == null) return result
    val lines = (result as? CommandResult.Output)?.lines.orEmpty()
    val exitCode = (result as? CommandResult.Output)?.exitCode ?: 0
    return try {
        output?.let { redirect -> files.write(redirect.target, lines.filterNot { it.isError }.map { it.text }, append = true) }
        errors?.let { redirect -> files.write(redirect.target, lines.filter { it.isError }.map { it.text }, append = true) }
        val kept = lines.filter { (it.isError && errors == null) || (!it.isError && output == null) }
        CommandResult.Output(kept, exitCode)
    } catch (e: RedirectException) {
        CommandResult.Output(listOf(OutputLine(e.message.orEmpty(), isError = true)))
    }
}

// Shows a command's result on the terminal. Returns its exit status, or null when it asked a question.
suspend fun showResult(result: CommandResult, emit: suspend (ShellEffect) -> Unit): Int? = when (result) {
    CommandResult.Clear -> {
        emit(ShellEffect.ClearScreen)
        0
    }
    is CommandResult.Output -> {
        result.lines.forEach { emit(ShellEffect.Print(it)) }
        result.exitCode
    }
    is CommandResult.Ask -> {
        result.lines.forEach { emit(ShellEffect.Print(it)) }
        emit(ShellEffect.Ask(result.question, result.answer))
        null
    }
    is CommandResult.Edit -> {
        emit(ShellEffect.Edit(result))
        0
    }
}
