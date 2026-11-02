// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import com.ilanmarchand.tui_launcher.shell.RedirectFiles
import org.junit.Assert.assertEquals
import org.junit.Test

// Expected outputs were taken from GNU coreutils, util-linux and GNU sed 4.10 on the PC
class MoreTextToolsTest {
    private val files = FileLines { name ->
        when (name) {
            "a.txt" -> FileRead.Lines(listOf("milk", "eggs"))
            "b.txt" -> FileRead.Lines(listOf("tea", "milk tea", "bread"))
            else -> FileRead.Failed("No such file or directory")
        }
    }

    private fun texts(result: CommandResult) = (result as CommandResult.Output).lines.map { it.text }

    @Test
    fun toolsReadFiles() {
        assertEquals(listOf("a.txt:milk", "b.txt:milk tea"), texts(grep(listOf("milk", "a.txt", "b.txt"), emptyList(), files)))
        assertEquals(listOf("a.txt:1", "b.txt:1"), texts(grep(listOf("-c", "milk", "a.txt", "b.txt"), emptyList(), files)))
        assertEquals(2, (grep(listOf("milk", "a.txt", "nosuch"), emptyList(), files) as CommandResult.Output).exitCode)
        assertEquals(listOf("==> a.txt <==", "milk", "", "==> b.txt <==", "tea"), texts(head(listOf("-n1", "a.txt", "b.txt"), emptyList(), files)))
        assertEquals(listOf(" 2  2 10 a.txt", " 3  4 19 b.txt", " 5  6 29 total"), texts(wc(listOf("a.txt", "b.txt"), emptyList(), files)))
        assertEquals(listOf(" 2 a.txt", " 3 b.txt", " 5 total"), texts(wc(listOf("-l", "a.txt", "b.txt"), emptyList(), files)))
        assertEquals(listOf("2 a.txt"), texts(wc(listOf("-l", "a.txt"), emptyList(), files)))
        assertEquals(listOf("bread", "eggs", "milk", "milk tea", "tea"), texts(sort(listOf("a.txt", "b.txt"), emptyList(), files)))
    }

    @Test
    fun trTranslatesDeletesSqueezes() {
        assertEquals(listOf("HELLO WORLD"), texts(tr(listOf("a-z", "A-Z"), listOf("Hello World"))))
        assertEquals(listOf("HELLO WORLD"), texts(tr(listOf("[:lower:]", "[:upper:]"), listOf("Hello World"))))
        assertEquals(listOf("abc"), texts(tr(listOf("-s", "a-c"), listOf("aabbcc"))))
        assertEquals(listOf("hello "), texts(tr(listOf("-d", "0-9"), listOf("hello 123"))))
        assertEquals(listOf("ll"), texts(tr(listOf("-cd", "l\\n"), listOf("hello"))))
        assertEquals(listOf("a", "b"), texts(tr(listOf(" ", "\\n"), listOf("a b"))))
        assertEquals(
            listOf("tr: missing operand after ‘a-z’", "Two strings must be given when translating.", "Try 'tr --help' for more information."),
            texts(tr(listOf("a-z"), listOf("x")))
        )
    }

    @Test
    fun cutFieldsAndCharacters() {
        assertEquals(listOf("b"), texts(cut(listOf("-d:", "-f2"), listOf("a:b:c:d"))))
        assertEquals(listOf("a:c"), texts(cut(listOf("-d", ":", "-f1,3"), listOf("a:b:c:d"))))
        assertEquals(listOf("b:c:d"), texts(cut(listOf("-d:", "-f2-"), listOf("a:b:c:d"))))
        assertEquals(listOf("ell"), texts(cut(listOf("-c2-4"), listOf("hello"))))
        assertEquals(listOf("nodelim"), texts(cut(listOf("-d:", "-f2"), listOf("nodelim"))))
        assertEquals(emptyList<String>(), texts(cut(listOf("-d:", "-s", "-f2"), listOf("nodelim"))))
        assertEquals(listOf("cut: the delimiter must be a single character", "Try 'cut --help' for more information."), texts(cut(listOf("-d", "ab", "-f1"), listOf("x"))))
    }

    @Test
    fun seqCounts() {
        assertEquals(listOf("1", "2", "3"), texts(seq(listOf("3"))))
        assertEquals(listOf("1", "3", "5", "7"), texts(seq(listOf("1", "2", "7"))))
        assertEquals(listOf("08", "09", "10"), texts(seq(listOf("-w", "8", "10"))))
        assertEquals(listOf("1,2,3"), texts(seq(listOf("-s,", "3"))))
        assertEquals(listOf("0.5", "1.0", "1.5", "2.0"), texts(seq(listOf("0.5", "0.5", "2"))))
        assertEquals(listOf("-1", "0", "1"), texts(seq(listOf("-1", "1"))))
        assertEquals(listOf("seq: invalid floating point argument: ‘x’", "Try 'seq --help' for more information."), texts(seq(listOf("x"))))
    }

    @Test
    fun tacRevNlTee() {
        assertEquals(listOf("c", "b", "a"), texts(tac(emptyList(), listOf("a", "b", "c"))))
        assertEquals(listOf("cba"), texts(rev(emptyList(), listOf("abc"))))
        assertEquals(listOf("     1\tone", "       ", "     2\ttwo"), texts(nl(emptyList(), listOf("one", "", "two"))))
        assertEquals(listOf("     1\tone", "     2\t", "     3\ttwo"), texts(nl(listOf("-ba"), listOf("one", "", "two"))))
        val written = mutableMapOf<String, List<String>>()
        val writer = object : RedirectFiles {
            override fun read(target: String) = written.getValue(target)
            override fun write(target: String, lines: List<String>, append: Boolean) {
                written[target] = (if (append) written[target].orEmpty() else emptyList()) + lines
            }
        }
        assertEquals(listOf("hi"), texts(tee(listOf("c.txt"), listOf("hi"), writer)))
        tee(listOf("-a", "c.txt"), listOf("more"), writer)
        assertEquals(listOf("hi", "more"), written["c.txt"])
    }

    private val text = listOf("foo bar", "baz foo", "qux")

    @Test
    fun sedSubstitutes() {
        assertEquals(listOf("f0o bar", "baz f0o", "qux"), texts(sed(listOf("s/o/0/"), text)))
        assertEquals("f00 bar", texts(sed(listOf("s/o/0/g"), text))[0])
        assertEquals("[foo] bar", texts(sed(listOf("s/foo/[&]/"), text))[0])
        assertEquals("oof bar", texts(sed(listOf("-E", "s/(f)(o+)/\\2\\1/"), text))[0])
        assertEquals("oof bar", texts(sed(listOf("s/\\(f\\)\\(o*\\)/\\2\\1/"), text))[0])
        assertEquals("x bar", texts(sed(listOf("s/FOO/x/I"), text))[0])
        assertEquals("fo0 bar", texts(sed(listOf("s/o/0/2"), text))[0])
        assertEquals("/ bar", texts(sed(listOf("s|foo|/|"), text))[0])
        assertEquals("foo cbr", texts(sed(listOf("s/a/b/;s/b/c/"), text))[0])
        assertEquals("Y bar", texts(sed(listOf("-e", "s/foo/X/", "-e", "s/X/Y/"), text))[0])
        assertEquals(listOf("F bar", "baz F"), texts(sed(listOf("-n", "s/foo/F/p"), text)))
    }

    @Test
    fun sedAddressesAndCommands() {
        assertEquals(listOf("baz foo"), texts(sed(listOf("-n", "2p"), text)))
        assertEquals(listOf("foo bar", "qux"), texts(sed(listOf("2d"), text)))
        assertEquals(listOf("qux"), texts(sed(listOf("/foo/d"), text)))
        assertEquals(listOf("foo bar", "baz foo"), texts(sed(listOf("-n", "/foo/p"), text)))
        assertEquals(listOf("qux"), texts(sed(listOf("1,2d"), text)))
        assertEquals(listOf("foo bar", "baz foo"), texts(sed(listOf("\$d"), text)))
        assertEquals(listOf("foo bar", "baz foo"), texts(sed(listOf("2q"), text)))
        assertEquals(listOf("qux"), texts(sed(listOf("-n", "\$p"), text)))
        assertEquals(listOf("baz foo"), texts(sed(listOf("2!d"), text)))
        assertEquals(listOf("qux"), texts(sed(listOf("-n", "3,1p"), text)))
        assertEquals(listOf("foo bar", "baz foo", "qux"), texts(sed(listOf("-n", "/foo/,/qux/p"), text)))
        assertEquals(listOf("1", "2", "3"), texts(sed(listOf("-n", "="), text)))
        assertEquals(listOf("f", "o bar"), texts(sed(listOf("s/o/\\n/"), text)).take(2))
    }

    @Test
    fun sedErrors() {
        assertEquals(listOf("sed: -e expression #1, char 5: unterminated 's' command"), texts(sed(listOf("s/a/b"), text)))
        assertEquals(listOf("sed: -e expression #1, char 1: unknown command: 'k'"), texts(sed(listOf("k"), text)))
        assertEquals(listOf("sed: -e expression #1, char 2: unknown command: 'k'"), texts(sed(listOf("2k"), text)))
        assertEquals(listOf("sed: -e expression #1, char 7: unknown option to 's'"), texts(sed(listOf("s/a/b/x"), text)))
        assertEquals(listOf("sed: -e expression #1, char 8: Unmatched ( or \\("), texts(sed(listOf("s/a\\(/b/"), text)))
        assertEquals(listOf("sed: can't read nosuch: No such file or directory"), texts(sed(listOf("-n", "p", "nosuch"), text, files)))
    }
}
