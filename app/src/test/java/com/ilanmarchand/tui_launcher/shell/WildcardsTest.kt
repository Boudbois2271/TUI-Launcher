package com.ilanmarchand.tui_launcher.shell

import com.ilanmarchand.tui_launcher.commands.splitShellWords
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WildcardsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var cwd: File

    @Before
    fun setUp() {
        cwd = temp.newFolder("home")
        for (name in listOf("notes/shop.md", "notes/todo.md", "notes/old.tmp", "a.md", "b.txt", "my list.md", ".hidden.md")) {
            File(cwd, name).apply { parentFile!!.mkdirs(); writeText("") }
        }
    }

    // What the command finally sees, after its own word splitting
    private fun words(text: String) = splitShellWords(expandWildcards(text, cwd))

    @Test
    fun starAndQuestionMark() {
        assertEquals(listOf("ls", "a.md", "my list.md"), words("ls *.md"))
        assertEquals(listOf("rm", "notes/old.tmp"), words("rm notes/*.tmp"))
        assertEquals(listOf("a.md"), words("?.md"))
        assertEquals(listOf("notes/shop.md", "notes/todo.md"), words("*/*.md"))
    }

    @Test
    fun sets() {
        assertEquals(listOf("a.md", "b.txt"), words("[ab].*"))
        assertEquals(listOf("b.txt"), words("[!a].txt"))
    }

    @Test
    fun hiddenFilesNeedADot() {
        assertEquals(listOf(".hidden.md"), words(".*.md"))
    }

    @Test
    fun noMatchStaysAsTyped() {
        assertEquals(listOf("*.zip"), words("*.zip"))
        assertEquals(listOf("call", "*#06#"), words("call *#06#"))
    }

    @Test
    fun quotesAndBackslashesStopWildcards() {
        assertEquals(listOf("*.md"), words("'*.md'"))
        assertEquals(listOf("*.md"), words("\"*.md\""))
        assertEquals(listOf("*.md"), words("\\*.md"))
        assertEquals(listOf("echo", "hi"), words("echo hi"))
    }

    @Test
    fun absolutePaths() {
        assertEquals(listOf("${cwd.path}/a.md", "${cwd.path}/my list.md"), words("${cwd.path}/*.md"))
    }
}
