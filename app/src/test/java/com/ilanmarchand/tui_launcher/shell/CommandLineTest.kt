package com.ilanmarchand.tui_launcher.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandLineTest {
    private fun parsed(vararg items: ListItem) = ParseResult.Parsed(items.toList())
    private fun error(message: String) = ParseResult.SyntaxError(message)
    private fun cmds(vararg texts: String) = texts.map { SimpleCommand(it) }

    @Test
    fun singleCommand() {
        assertEquals(parsed(ListItem(null, cmds("nmcli radio wifi"))), parseCommandLine("nmcli radio wifi"))
        assertEquals(parsed(), parseCommandLine(""))
        assertEquals(parsed(), parseCommandLine("   "))
    }

    @Test
    fun pipelinesAndLists() {
        assertEquals(
            parsed(ListItem(null, cmds("contacts list", "grep -i paul", "wc -l"))),
            parseCommandLine("contacts list | grep -i paul | wc -l")
        )
        assertEquals(
            parsed(
                ListItem(null, cmds("a")),
                ListItem(Connector.AND, cmds("b", "c")),
                ListItem(Connector.OR, cmds("d")),
                ListItem(Connector.THEN, cmds("e"))
            ),
            parseCommandLine("a && b | c || d ; e")
        )
        assertEquals(parsed(ListItem(null, cmds("a"))), parseCommandLine("a;"))
    }

    @Test
    fun quotesProtectOperators() {
        assertEquals(parsed(ListItem(null, cmds("echo 'a | b' \"c && d\""))), parseCommandLine("echo 'a | b' \"c && d\""))
        assertEquals(parsed(ListItem(null, cmds("echo a\\;b"))), parseCommandLine("echo a\\;b"))
        assertEquals(parsed(ListItem(null, cmds("'Red & moi'"))), parseCommandLine("'Red & moi'"))
    }

    @Test
    fun comments() {
        assertEquals(parsed(ListItem(null, cmds("echo a"))), parseCommandLine("echo a # note"))
        assertEquals(parsed(ListItem(null, cmds("call *#06#"))), parseCommandLine("call *#06#"))
        assertEquals(parsed(), parseCommandLine("# just a note"))
    }

    @Test
    fun bashSyntaxErrors() {
        assertEquals(error("syntax error near unexpected token `|'"), parseCommandLine("| a"))
        assertEquals(error("syntax error near unexpected token `;'"), parseCommandLine("; a"))
        assertEquals(error("syntax error near unexpected token `&&'"), parseCommandLine("a && && b"))
        assertEquals(error("syntax error near unexpected token `;;'"), parseCommandLine("a ;; b"))
        assertEquals(error("syntax error: unexpected end of file"), parseCommandLine("echo a &&"))
        assertEquals(error("syntax error: unexpected end of file"), parseCommandLine("echo a |"))
        assertEquals(error("unexpected EOF while looking for matching `''"), parseCommandLine("echo 'a"))
    }

    @Test
    fun unsupportedShellFeatures() {
        assertEquals(error("background jobs (&) are not available here"), parseCommandLine("Red & moi"))
        assertEquals(error("redirections to another output (>&) are not available here"), parseCommandLine("ls nope 2>&1"))
    }

    @Test
    fun redirections() {
        assertEquals(
            parsed(ListItem(null, listOf(SimpleCommand("echo milk", listOf(Redirect(RedirectKind.APPEND, "notes/shop.md")))))),
            parseCommandLine("echo milk >> notes/shop.md")
        )
        assertEquals(
            parsed(
                ListItem(
                    null,
                    listOf(
                        SimpleCommand("sort", listOf(Redirect(RedirectKind.READ, "in.txt"))),
                        SimpleCommand("uniq", listOf(Redirect(RedirectKind.WRITE, "my list.txt"), Redirect(RedirectKind.ERRORS_WRITE, "/dev/null")))
                    )
                )
            ),
            parseCommandLine("sort < in.txt | uniq >'my list.txt' 2>/dev/null")
        )
        // A 2 inside a word is part of it; > alone empties a file
        assertEquals(parsed(ListItem(null, listOf(SimpleCommand("echo a2", listOf(Redirect(RedirectKind.WRITE, "f")))))), parseCommandLine("echo a2>f"))
        assertEquals(parsed(ListItem(null, listOf(SimpleCommand("", listOf(Redirect(RedirectKind.WRITE, "f")))))), parseCommandLine("> f"))
        assertEquals(error("syntax error near unexpected token `newline'"), parseCommandLine("echo >"))
        assertEquals(error("syntax error near unexpected token `|'"), parseCommandLine("echo > | x"))
    }
}
