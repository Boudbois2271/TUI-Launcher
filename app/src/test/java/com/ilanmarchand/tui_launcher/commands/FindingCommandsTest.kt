// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import com.ilanmarchand.tui_launcher.apps.LaunchableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingCommandsTest {
    private val apps = listOf(
        LaunchableApp("Instagram", "com.instagram.android", "Main"),
        LaunchableApp("Contacts", "com.google.android.contacts", "People")
    )
    private val aliases = mapOf("ig" to "instagram")

    private fun out(vararg lines: String, exitCode: Int = 0) =
        CommandResult.Output(lines.map { OutputLine(it) }, exitCode)

    @Test
    fun everyCommandIsDocumented() {
        for (command in builtinCommands) {
            assertTrue(command.name, command.synopsis.startsWith(command.name))
            assertTrue(command.name, command.summary.isNotBlank())
        }
    }

    @Test
    fun helpListsAndDescribes() {
        val list = help(emptyList(), builtinCommands) as CommandResult.Output
        assertTrue(list.lines.any { it.text == " alias [-p] [name[=value] ... ]" })
        assertEquals(out("alias: alias [-p] [name[=value] ... ]", "    Define or display aliases."), help(listOf("alias"), builtinCommands))
        // Like bash, a pattern matches the start of the name
        assertEquals(out("echo: echo [-neE] [arg ...]", "    Write arguments to the standard output."), help(listOf("ec"), builtinCommands))
        assertEquals(
            CommandResult.Output(listOf(OutputLine("help: no help topics match `nosuch'.  Try `help help'.", isError = true))),
            help(listOf("nosuch"), builtinCommands)
        )
    }

    @Test
    fun typeSaysWhatRuns() {
        assertEquals(out("ig is aliased to `instagram'"), typeOf(listOf("ig"), aliases, builtinCommands, apps))
        assertEquals(out("echo is a shell builtin"), typeOf(listOf("echo"), aliases, builtinCommands, apps))
        assertEquals(
            out("instagram is the app Instagram (com.instagram.android)"),
            typeOf(listOf("instagram"), aliases, builtinCommands, apps)
        )
        assertEquals(
            out(
                "contacts is a shell builtin when given arguments",
                "contacts alone is the app Contacts (com.google.android.contacts)"
            ),
            typeOf(listOf("contacts"), aliases, builtinCommands, apps)
        )
        assertEquals(out("alias", "builtin", "file"), typeOf(listOf("-t", "ig", "grep", "instagram"), aliases, builtinCommands, apps))
        assertEquals(
            CommandResult.Output(listOf(OutputLine("type: nosuch: not found", isError = true)), 1),
            typeOf(listOf("nosuch"), aliases, builtinCommands, apps)
        )
        assertEquals(out(exitCode = 1), typeOf(listOf("-t", "nosuch"), aliases, builtinCommands, apps))
    }

    @Test
    fun compgenListsNames() {
        assertEquals(out("ig"), compgen(listOf("-a"), aliases, builtinCommands, apps))
        assertEquals(out("echo"), compgen(listOf("-b", "ec"), aliases, builtinCommands, apps))
        assertEquals(out("ig", "Instagram"), compgen(listOf("-c", "i"), aliases, builtinCommands, apps))
        assertEquals(out(exitCode = 1), compgen(listOf("-a", "zz"), aliases, builtinCommands, apps))
        assertEquals(
            CommandResult.Output(
                listOf(
                    OutputLine("compgen: -z: invalid option", isError = true),
                    OutputLine("compgen: usage: compgen [-abc] [word]", isError = true)
                ),
                2
            ),
            compgen(listOf("-z"), aliases, builtinCommands, apps)
        )
    }
}
