package com.ilanmarchand.tui_launcher.commands

import android.content.Context

// What a built-in command asks the terminal to do
sealed interface CommandResult {
    // Empties the terminal output, like clear in a shell
    data object Clear : CommandResult

    // Lines printed in order, errors mixed with normal output as in a shell.
    // No lines prints nothing, like a successful shell command.
    data class Output(val lines: List<OutputLine>) : CommandResult
}

data class OutputLine(val text: String, val isError: Boolean = false)

// A command typed by its name, like an app. Each one lives in its own file.
// run() is called on a background thread, so it may read files and preferences.
interface BuiltinCommand {
    val name: String

    // A command that takes arguments matches its name followed by a space, and gets the rest of the line.
    // Other commands match only their name, so extra words make the line unknown.
    val takesArgs: Boolean
        get() = false

    fun run(context: Context, args: String): CommandResult
}

// A typed line that names a built-in command, with the text after the name
data class BuiltinCall(val command: BuiltinCommand, val args: String)

// Every built-in command is listed here. Adding one is a line here plus its own file.
val builtinCommands: List<BuiltinCommand> = listOf(
    ClearCommand,
    LogCommand,
    AliasCommand,
    UnaliasCommand,
    DpmCommand,
    RebootCommand,
    NmcliCommand
)

fun findBuiltinCommand(input: String): BuiltinCall? {
    val firstWord = input.substringBefore(' ')
    val args = input.substringAfter(' ', "").trim()
    for (command in builtinCommands) {
        if (command.name.equals(input, ignoreCase = true)) return BuiltinCall(command, "")
        if (command.takesArgs && command.name.equals(firstWord, ignoreCase = true)) {
            return BuiltinCall(command, args)
        }
    }
    return null
}
