package com.ilanmarchand.tui_launcher.commands

import android.content.Context

// What a built-in command asks the terminal to do
sealed interface CommandResult {
    // Empties the terminal output, like clear in a shell
    data object Clear : CommandResult

    // Lines printed in order, errors mixed with normal output as in a shell.
    // No lines prints nothing, like a successful shell command. The exit status is 1 when an error line
    // was printed, unless the command says otherwise (grep exits 1 when nothing matches).
    data class Output(
        val lines: List<OutputLine>,
        val exitCode: Int = if (lines.any { it.isError }) 1 else 0
    ) : CommandResult

    // Prints the lines, then asks a question, like pacman's [Y/n]. The question takes the prompt's place, and
    // the next typed line is the answer: answer() runs off the main thread and its result is shown.
    data class Ask(
        val lines: List<OutputLine>,
        val question: String,
        val answer: (String) -> CommandResult
    ) : CommandResult
}

// A command that does more as root, reached through sudo (pacman -R and -S)
interface RootCommand : BuiltinCommand {
    fun runAsRoot(context: Context, args: String): CommandResult
}

// accentLength: how many leading characters are drawn in the accent color, such as fastfetch's keys.
// A pipe passes only the text on, like a real tool that drops colors when piped.
data class OutputLine(val text: String, val isError: Boolean = false, val accentLength: Int = 0)

// A command typed by its name, like an app. Each one lives in its own file.
// run() is called on a background thread, so it may read files and preferences.
interface BuiltinCommand {
    val name: String

    // For `help`: how to call it, like bash's help list, and what it does in one line
    val synopsis: String
    val summary: String

    // A command that takes arguments matches its name followed by a space, and gets the rest of the line.
    // Other commands match only their name, so extra words make the line unknown.
    val takesArgs: Boolean
        get() = false

    // A command that does nothing without arguments leaves its bare name free, so the name alone works like
    // any other word: `contacts` opens the Contacts app, while `contacts list` is the command
    val needsArgs: Boolean
        get() = false

    fun run(context: Context, args: String): CommandResult

    // In a pipeline, stdin is the output of the command before (null when there is none). Commands that
    // read text, like grep, use it; the others ignore it.
    fun run(context: Context, args: String, stdin: List<String>?): CommandResult = run(context, args)
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
    NmcliCommand,
    LoginctlCommand,
    WpctlCommand,
    BrightnessctlCommand,
    ContactsCommand,
    CallCommand,
    EchoCommand,
    GrepCommand,
    HeadCommand,
    TailCommand,
    WcCommand,
    SortCommand,
    UniqCommand,
    HelpCommand,
    TypeCommand,
    CompgenCommand,
    DateCommand,
    UptimeCommand,
    UnameCommand,
    WhoamiCommand,
    HostnameCommand,
    FreeCommand,
    DfCommand,
    CalCommand,
    AcpiCommand,
    FastfetchCommand,
    PlayerctlCommand,
    GrimCommand,
    XdgOpenCommand,
    HistoryCommand,
    SudoCommand,
    PacmanCommand,
    PwdCommand,
    CdCommand,
    LsCommand,
    CatCommand,
    MkdirCommand,
    RmCommand,
    TouchCommand,
    MvCommand,
    CpCommand
)

fun findBuiltinCommand(input: String): BuiltinCall? {
    val firstWord = input.substringBefore(' ')
    val args = input.substringAfter(' ', "").trim()
    for (command in builtinCommands) {
        if (command.name.equals(input, ignoreCase = true)) {
            if (command.needsArgs) continue
            return BuiltinCall(command, "")
        }
        if (command.takesArgs && args.isNotEmpty() && command.name.equals(firstWord, ignoreCase = true)) {
            return BuiltinCall(command, args)
        }
    }
    return null
}
