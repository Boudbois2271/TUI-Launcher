package com.ilanmarchand.tui_launcher.files

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine
import com.ilanmarchand.tui_launcher.commands.nano
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NanoTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun opensFiles() {
        val home = temp.newFolder("home")
        File(home, "notes").mkdir()
        File(home, "notes/shop.md").writeText("milk\neggs\n")
        val workspace = Workspace(home)
        assertEquals(CommandResult.Edit(File(home, "notes/shop.md").path, "notes/shop.md", "milk\neggs\n", isNew = false), nano(listOf("notes/shop.md"), workspace))
        assertEquals(CommandResult.Edit(File(home, "new.md").path, "new.md", "", isNew = true), nano(listOf("new.md"), workspace))
        assertEquals(CommandResult.Output(listOf(OutputLine("nano: \"notes\" is a directory", isError = true))), nano(listOf("notes"), workspace))
    }

    @Test
    fun writesWholeFiles() {
        val home = temp.newFolder("home")
        val log = File(home, "log.txt").apply { writeText("keep\n") }
        val workspace = Workspace(home, setOf(log))
        val file = File(home, "a.md")
        assertNull(writeWholeFile(workspace, file.path, "one\ntwo"))
        assertEquals("one\ntwo\n", file.readText())
        assertEquals("Operation not permitted", writeWholeFile(workspace, log.path, "x"))
        assertEquals("keep\n", log.readText())
        assertEquals("No such file or directory", writeWholeFile(workspace, File(home, "x/y.md").path, "x"))
    }

    @Test
    fun linesAsNanoCountsThem() {
        assertEquals(0, nanoLineCount(""))
        assertEquals(2, nanoLineCount("milk\neggs\n"))
        assertEquals(2, nanoLineCount("milk\neggs"))
        assertEquals("[ Read 1 line ]", nanoLines("Read", 1))
        assertEquals("[ Wrote 3 lines ]", nanoLines("Wrote", 3))
    }
}
