package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine

// What running a line does to the terminal, in order
sealed interface ShellEffect {
    data class Print(val line: OutputLine) : ShellEffect
    data object ClearScreen : ShellEffect

    // The next typed line answers the question instead of being run
    data class Ask(val question: String, val answer: (String) -> CommandResult) : ShellEffect
}

// Runs one command, already alias-expanded, with the output of the command before it in a pipeline
// (null for the first command)
fun interface CommandRunner {
    suspend fun run(command: String, stdin: List<String>?): CommandResult
}

// Runs a parsed command list like bash: after ; the next pipeline always runs, after && only if the last
// one succeeded, after || only if it failed. Aliases are expanded for each command. Returns the exit status
// of the last pipeline that ran. A command that asks a question ends the line there: the answer comes with
// the next typed line, and the rest of the line is dropped.
suspend fun runCommandList(
    items: List<ListItem>,
    expand: (String) -> String,
    runner: CommandRunner,
    emit: suspend (ShellEffect) -> Unit
): Int {
    var status = 0
    for (item in items) {
        val runs = when (item.connector) {
            null, Connector.THEN -> true
            Connector.AND -> status == 0
            Connector.OR -> status != 0
        }
        if (runs) status = runPipeline(item.pipeline, expand, runner, emit) ?: return status
    }
    return status
}

// Each command's output, without its error lines, is the next command's input. Error lines are printed
// as they come, and the last command's lines are printed in their own order. The status is the last
// command's, or null when a command asked a question.
private suspend fun runPipeline(
    pipeline: List<String>,
    expand: (String) -> String,
    runner: CommandRunner,
    emit: suspend (ShellEffect) -> Unit
): Int? {
    var stdin: List<String>? = null
    var status = 0
    for ((index, command) in pipeline.withIndex()) {
        val result = runner.run(expand(command), stdin)
        if (index == pipeline.lastIndex || result is CommandResult.Ask) return showResult(result, emit)
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
            is CommandResult.Ask -> Unit
        }
    }
    return status
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
}
