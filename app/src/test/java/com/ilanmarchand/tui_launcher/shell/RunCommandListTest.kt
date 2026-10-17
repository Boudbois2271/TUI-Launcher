package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RunCommandListTest {
    // A fake shell: `true`, `false`, `say WORDS`, `fail WORDS` (an error line), `clear`, and `upper`, which
    // prints its input in capitals. Every command it runs is recorded.
    private val ran = mutableListOf<String>()
    private val runner = CommandRunner { command, stdin ->
        ran += command
        val words = command.split(' ')
        when (words[0]) {
            "true" -> CommandResult.Output(emptyList())
            "false" -> CommandResult.Output(emptyList(), exitCode = 1)
            "say" -> CommandResult.Output(listOf(OutputLine(words.drop(1).joinToString(" "))))
            "fail" -> CommandResult.Output(listOf(OutputLine(words.drop(1).joinToString(" "), isError = true)))
            "clear" -> CommandResult.Clear
            "upper" -> CommandResult.Output(stdin.orEmpty().map { OutputLine(it.uppercase()) })
            "ask" -> CommandResult.Ask(listOf(OutputLine("sure?")), "[Y/n]") { CommandResult.Output(emptyList()) }
            else -> CommandResult.Output(listOf(OutputLine("command not found: $command", isError = true)), 127)
        }
    }

    // In-memory files for redirections; "dir" stands for a folder
    private val files = mutableMapOf("in.txt" to listOf("b", "a"))
    private val fakeFiles = object : RedirectFiles {
        override fun read(target: String) = files[target] ?: throw RedirectException("$target: No such file or directory")
        override fun write(target: String, lines: List<String>, append: Boolean) {
            if (target == "dir") throw RedirectException("dir: Is a directory")
            files[target] = (if (append) files[target].orEmpty() else emptyList()) + lines
        }
    }

    private fun run(line: String, aliases: Map<String, String> = emptyMap()): Pair<List<ShellEffect>, Int> {
        val effects = mutableListOf<ShellEffect>()
        val items = (parseCommandLine(line) as ParseResult.Parsed).items
        val status = runBlocking {
            runCommandList(items, { aliases[it.substringBefore(' ')]?.let { value -> value + it.drop(it.substringBefore(' ').length) } ?: it }, runner, fakeFiles) {
                effects += it
            }
        }
        return effects to status
    }

    private fun printed(vararg lines: String) = lines.map { ShellEffect.Print(OutputLine(it)) }

    @Test
    fun andRunsOnlyAfterSuccess() {
        assertEquals(printed("yes") to 0, run("true && say yes"))
        assertEquals(emptyList<ShellEffect>() to 1, run("false && say yes"))
    }

    @Test
    fun orRunsOnlyAfterFailure() {
        assertEquals(printed("fallback") to 0, run("false || say fallback"))
        assertEquals(emptyList<ShellEffect>() to 0, run("true || say fallback"))
    }

    @Test
    fun skippedCommandsKeepTheLastStatus() {
        assertEquals(printed("c") to 0, run("false && say b || say c"))
        assertEquals(listOf("false", "say c"), ran)
    }

    @Test
    fun semicolonAlwaysRuns() {
        assertEquals(printed("a", "b") to 0, run("say a; false; say b"))
    }

    @Test
    fun pipesFeedOutputButShowErrors() {
        assertEquals(printed("HELLO") to 0, run("say hello | upper"))
        assertEquals(
            listOf(ShellEffect.Print(OutputLine("oops", isError = true))) to 0,
            run("fail oops | upper")
        )
    }

    @Test
    fun unknownCommandExits127() {
        assertEquals(127, run("nosuchcmd").second)
        assertEquals(printed("handled") to 0, run("nosuchcmd || say handled").let { (effects, status) -> effects.drop(1) to status })
    }

    @Test
    fun clearOnlyWhenLastInPipeline() {
        assertEquals(listOf(ShellEffect.ClearScreen) + printed("after"), run("clear; say after").first)
        assertEquals(emptyList<ShellEffect>(), run("clear | upper").first)
    }

    @Test
    fun aliasesExpandForEachCommand() {
        val aliases = mapOf("hi" to "say hi", "up" to "upper")
        assertEquals(printed("HI THERE") to 0, run("hi there | up", aliases))
    }

    @Test
    fun aQuestionEndsTheLine() {
        val (effects, _) = run("say before; ask; say after")
        assertEquals(ShellEffect.Print(OutputLine("before")), effects[0])
        assertEquals(ShellEffect.Print(OutputLine("sure?")), effects[1])
        assertEquals("[Y/n]", (effects[2] as ShellEffect.Ask).question)
        assertEquals(3, effects.size)
    }

    @Test
    fun outputGoesToFiles() {
        assertEquals(emptyList<ShellEffect>() to 0, run("say milk > list; say eggs >> list"))
        assertEquals(listOf("milk", "eggs"), files["list"])
        run("say only > list")
        assertEquals(listOf("only"), files["list"])
    }

    @Test
    fun inputComesFromFiles() {
        assertEquals(printed("B", "A") to 0, run("upper < in.txt"))
        assertEquals(
            listOf(ShellEffect.Print(OutputLine("nope: No such file or directory", isError = true))) to 1,
            run("upper < nope")
        )
        // The command didn't run
        assertEquals(emptyList<String>(), ran.filter { it == "upper" }.drop(1))
    }

    @Test
    fun errorsCanGoElsewhere() {
        assertEquals(emptyList<ShellEffect>() to 1, run("fail oops 2> errors"))
        assertEquals(listOf("oops"), files["errors"])
        assertEquals(
            listOf(ShellEffect.Print(OutputLine("dir: Is a directory", isError = true))) to 1,
            run("say x > dir")
        )
    }
}
