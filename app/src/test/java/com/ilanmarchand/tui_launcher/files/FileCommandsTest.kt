package com.ilanmarchand.tui_launcher.files

import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine
import com.ilanmarchand.tui_launcher.commands.cat
import com.ilanmarchand.tui_launcher.commands.cd
import com.ilanmarchand.tui_launcher.commands.cp
import com.ilanmarchand.tui_launcher.commands.ls
import com.ilanmarchand.tui_launcher.commands.mkdir
import com.ilanmarchand.tui_launcher.commands.mv
import com.ilanmarchand.tui_launcher.commands.rm
import com.ilanmarchand.tui_launcher.commands.touch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Messages were checked against GNU coreutils and bash on the PC, in its en_US.UTF-8 locale
class FileCommandsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var home: File
    private lateinit var log: File
    private lateinit var workspace: Workspace

    @Before
    fun setUp() {
        home = temp.newFolder("home")
        File(home, "notes").mkdir()
        File(home, "notes/shop.md").writeText("milk\neggs\n")
        File(home, ".hidden").writeText("")
        log = File(home, "Android/data/app/files/terminal-log.txt").apply { parentFile!!.mkdirs(); writeText("x\n") }
        workspace = Workspace(home, setOf(log))
    }

    private fun out(vararg lines: String) = CommandResult.Output(lines.map { OutputLine(it) })
    private fun err(vararg lines: String, exitCode: Int = 1) = CommandResult.Output(lines.map { OutputLine(it, isError = true) }, exitCode)
    private fun texts(result: CommandResult) = (result as CommandResult.Output).lines.map { it.text }

    @Test
    fun pathsResolveLikeAShell() {
        assertEquals(File(home, "notes"), workspace.resolve("notes"))
        assertEquals(File(home, "notes"), workspace.resolve("~/notes/../notes/."))
        assertEquals(File("/etc"), workspace.resolve("/etc"))
    }

    @Test
    fun cdAndBack() {
        assertEquals(out(), cd(listOf("notes"), workspace))
        assertEquals(File(home, "notes"), workspace.cwd)
        assertEquals(out(home.path), cd(listOf("-"), workspace))
        assertEquals(err("cd: nosuch: No such file or directory"), cd(listOf("nosuch"), workspace))
        assertEquals(err("cd: notes/shop.md: Not a directory"), cd(listOf("notes/shop.md"), workspace))
        cd(listOf("notes"), workspace)
        cd(emptyList(), workspace)
        assertEquals(home, workspace.cwd)
    }

    @Test
    fun lsLists() {
        assertEquals(listOf("Android", "notes"), texts(ls(emptyList(), workspace, 0)))
        assertEquals(listOf(".", "..", ".hidden", "Android", "notes"), texts(ls(listOf("-a"), workspace, 0)))
        assertEquals(listOf(".hidden", "Android", "notes"), texts(ls(listOf("-A"), workspace, 0)))
        assertEquals(
            CommandResult.Output(
                listOf(OutputLine("ls: cannot access 'nosuch': No such file or directory", isError = true), OutputLine("notes:"), OutputLine("shop.md")),
                exitCode = 2
            ),
            ls(listOf("notes", "nosuch"), workspace, 0)
        )
        // Directories are drawn in the accent color
        assertEquals(OutputLine("notes", accentLength = 5), (ls(emptyList(), workspace, 0) as CommandResult.Output).lines[1])
        val long = texts(ls(listOf("-l", "notes"), workspace, System.currentTimeMillis()))
        assertEquals("total 4", long[0])
        assertTrue(long[1], long[1].matches(Regex("""-rw[-x][-r][-w][-x][-r][-w][-x] +\d+ \S+ +\S+ +10 \w{3} [ \d]\d \d\d:\d\d shop\.md""")))
    }

    @Test
    fun catPrints() {
        assertEquals(out("milk", "eggs"), cat(listOf("notes/shop.md"), emptyList(), workspace))
        assertEquals(out("     1\tmilk", "     2\teggs"), cat(listOf("-n", "notes/shop.md"), emptyList(), workspace))
        assertEquals(out("piped"), cat(emptyList(), listOf("piped"), workspace))
        assertEquals(err("cat: notes: Is a directory"), cat(listOf("notes"), emptyList(), workspace))
        assertEquals(err("cat: nosuch: No such file or directory"), cat(listOf("nosuch"), emptyList(), workspace))
    }

    @Test
    fun mkdirMakes() {
        assertEquals(err("mkdir: cannot create directory ‘notes’: File exists"), mkdir(listOf("notes"), workspace))
        assertEquals(err("mkdir: cannot create directory ‘a/b’: No such file or directory"), mkdir(listOf("a/b"), workspace))
        assertEquals(out(), mkdir(listOf("-p", "a/b"), workspace))
        assertTrue(File(home, "a/b").isDirectory)
        assertEquals(err("mkdir: missing operand", "Try 'mkdir --help' for more information."), mkdir(emptyList(), workspace))
    }

    @Test
    fun rmRemovesButNotTheLog() {
        assertEquals(err("rm: cannot remove 'notes': Is a directory"), rm(listOf("notes"), workspace))
        assertEquals(err("rm: cannot remove 'nosuch': No such file or directory"), rm(listOf("nosuch"), workspace))
        assertEquals(out(), rm(listOf("-f", "nosuch"), workspace))
        assertEquals(
            err("rm: cannot remove 'Android/data/app/files/terminal-log.txt': Operation not permitted"),
            rm(listOf("Android/data/app/files/terminal-log.txt"), workspace)
        )
        val recursive = texts(rm(listOf("-r", "Android"), workspace))
        assertEquals("rm: cannot remove 'Android/data/app/files/terminal-log.txt': Operation not permitted", recursive.first())
        assertTrue(log.exists())
        assertEquals(out(), rm(listOf("-r", "notes"), workspace))
        assertFalse(File(home, "notes").exists())
        assertEquals(
            err("rm: it is dangerous to operate recursively on '/'", "rm: use --no-preserve-root to override this failsafe"),
            rm(listOf("-rf", "/"), workspace)
        )
    }

    @Test
    fun touchMakesFiles() {
        assertEquals(out(), touch(listOf("new.txt"), workspace, 0))
        assertTrue(File(home, "new.txt").exists())
        assertEquals(err("touch: cannot touch 'x/y': No such file or directory"), touch(listOf("x/y"), workspace, 0))
        assertEquals(err("touch: missing file operand", "Try 'touch --help' for more information."), touch(emptyList(), workspace, 0))
    }

    @Test
    fun mvAndCp() {
        assertEquals(err("mv: cannot stat 'nosuch': No such file or directory"), mv(listOf("nosuch", "z"), workspace))
        assertEquals(err("cp: cannot stat 'nosuch': No such file or directory"), cp(listOf("nosuch", "z"), workspace))
        assertEquals(err("cp: -r not specified; omitting directory 'notes'"), cp(listOf("notes", "z"), workspace))
        assertEquals(
            err("cp: missing destination file operand after 'notes/shop.md'", "Try 'cp --help' for more information."),
            cp(listOf("notes/shop.md"), workspace)
        )
        assertEquals(out(), cp(listOf("-r", "notes", "notes2"), workspace))
        assertEquals("milk\neggs\n", File(home, "notes2/shop.md").readText())
        assertEquals(out(), mv(listOf("notes2", "notes3"), workspace))
        assertTrue(File(home, "notes3/shop.md").exists())
        assertEquals(out(), mv(listOf("notes3/shop.md", "notes"), workspace))
        assertTrue(File(home, "notes/shop.md").exists())
        assertEquals(
            err("mv: cannot move 'Android' to 'elsewhere': Operation not permitted"),
            mv(listOf("Android", "elsewhere"), workspace)
        )
    }
}
