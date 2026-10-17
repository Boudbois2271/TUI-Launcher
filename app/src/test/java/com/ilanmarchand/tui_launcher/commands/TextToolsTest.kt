package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Test

// Expected outputs were taken from the real GNU tools and bash on the PC
class TextToolsTest {
    private val fruits = listOf("apple", "Banana", "cherry")

    private fun out(vararg lines: String, exitCode: Int = 0) =
        CommandResult.Output(lines.map { OutputLine(it) }, exitCode)

    private fun err(vararg lines: String, exitCode: Int = 1) =
        CommandResult.Output(lines.map { OutputLine(it, isError = true) }, exitCode)

    @Test
    fun echoPrintsItsWords() {
        assertEquals(out("a b"), echo(listOf("a", "b")))
        assertEquals(out(""), echo(emptyList()))
        assertEquals(out("a\tb", "c"), echo(listOf("-e", "a\\tb\\nc")))
        assertEquals(out("a\\nb"), echo(listOf("a\\nb")))
        assertEquals(out("x"), echo(listOf("-n", "x")))
        assertEquals(out("-- -n"), echo(listOf("--", "-n")))
    }

    @Test
    fun grepSelectsLines() {
        assertEquals(out("Banana"), grep(listOf("an"), fruits))
        assertEquals(out("1"), grep(listOf("-c", "an"), fruits))
        assertEquals(out("2:Banana"), grep(listOf("-n", "-i", "b"), fruits))
        assertEquals(out("apple", "cherry"), grep(listOf("-v", "Banana"), fruits))
        assertEquals(out("Foo"), grep(listOf("-ie", "foo"), listOf("Foo")))
        assertEquals(out("2"), grep(listOf("-c", "-v", "foo"), listOf("Foo", "bar")))
        assertEquals(out(exitCode = 1), grep(listOf("zzz"), fruits))
        assertEquals(out(exitCode = 0), grep(listOf("-q", "apple"), fruits))
    }

    @Test
    fun grepBasicAndExtendedRegex() {
        val lines = listOf("a+b", "aab")
        assertEquals(out("a+b"), grep(listOf("a+b"), lines))
        assertEquals(out("aab"), grep(listOf("a\\+b"), lines))
        assertEquals(out("aab"), grep(listOf("-E", "a+b"), lines))
        assertEquals(out("a+b"), grep(listOf("-F", "a+b"), lines))
        assertEquals(out("x1"), grep(listOf("[[:digit:]]"), listOf("x1", "y")))
        assertEquals(out("foo"), grep(listOf("-w", "foo"), listOf("foo", "foobar")))
        assertEquals(out("foobar"), grep(listOf("-x", "foobar"), listOf("foo", "foobar")))
        assertEquals(out("a b"), grep(listOf("-E", "a|z"), listOf("a b", "c")))
    }

    @Test
    fun grepErrors() {
        val usage = arrayOf("Usage: grep [OPTION]... PATTERNS [FILE]...", "Try 'grep --help' for more information.")
        assertEquals(err(*usage, exitCode = 2), grep(emptyList(), fruits))
        assertEquals(err("grep: unrecognized option '--nope'", *usage, exitCode = 2), grep(listOf("--nope", "a"), fruits))
        assertEquals(err("grep: Unmatched ( or \\(", exitCode = 2), grep(listOf("a\\("), fruits))
        assertEquals(err("grep: Unmatched ( or \\(", exitCode = 2), grep(listOf("-E", "a("), fruits))
        assertEquals(err("grep: nosuchfile: No such file or directory", exitCode = 2), grep(listOf("a", "nosuchfile"), fruits))
    }

    @Test
    fun headAndTail() {
        val numbers = (1..20).map { it.toString() }
        assertEquals(out(*(1..10).map { "$it" }.toTypedArray()), head(emptyList(), numbers))
        assertEquals(out("1", "2", "3"), head(listOf("-n", "3"), numbers))
        assertEquals(out("1", "2", "3"), head(listOf("-3"), numbers))
        assertEquals(out("1", "2"), head(listOf("-n", "-18"), numbers))
        assertEquals(out("19", "20"), tail(listOf("-n", "2"), numbers))
        assertEquals(out("19", "20"), tail(listOf("-2"), numbers))
        assertEquals(out("4", "5"), tail(listOf("-n", "+4"), (1..5).map { "$it" }))
        assertEquals(err("head: invalid number of lines: ‘x’"), head(listOf("-n", "x"), numbers))
        assertEquals(err("tail: invalid number of lines: ‘x’"), tail(listOf("-n", "x"), numbers))
        assertEquals(err("head: invalid option -- 'z'", "Try 'head --help' for more information."), head(listOf("-z"), numbers))
    }

    @Test
    fun wcCounts() {
        val text = listOf("a b", "c")
        assertEquals(out("      2       3       6"), wc(emptyList(), text))
        assertEquals(out("2"), wc(listOf("-l"), text))
        assertEquals(out("      2       3"), wc(listOf("-lw"), text))
        assertEquals(out("3"), wc(listOf("-c"), listOf("é")))
        assertEquals(out("2"), wc(listOf("-m"), listOf("é")))
        assertEquals(out("      0       0       0"), wc(emptyList(), emptyList()))
        assertEquals(err("wc: invalid option -- 'z'", "Try 'wc --help' for more information."), wc(listOf("-z"), text))
    }

    @Test
    fun sortOrders() {
        val lines = listOf("b", "A", "a", "10", "9")
        assertEquals(out("10", "9", "a", "A", "b"), sort(emptyList(), lines))
        assertEquals(out("2", "9", "10"), sort(listOf("-n"), listOf("10", "9", "2")))
        assertEquals(out("10", "9", "2"), sort(listOf("-rn"), listOf("10", "9", "2")))
        assertEquals(out("a", "b"), sort(listOf("-u"), listOf("b", "a", "b")))
    }

    @Test
    fun uniqFoldsNeighbors() {
        assertEquals(out("      2 a", "      1 b", "      1 a"), uniq(listOf("-c"), listOf("a", "a", "b", "a")))
        assertEquals(out("a"), uniq(listOf("-d"), listOf("a", "a", "b")))
        assertEquals(out("b"), uniq(listOf("-u"), listOf("a", "a", "b")))
        assertEquals(out("A"), uniq(listOf("-i"), listOf("A", "a")))
    }
}
