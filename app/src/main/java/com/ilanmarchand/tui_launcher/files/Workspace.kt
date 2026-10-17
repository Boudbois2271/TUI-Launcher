package com.ilanmarchand.tui_launcher.files

import android.content.Context
import android.os.Environment
import com.ilanmarchand.tui_launcher.log.commandLogFile
import java.io.File

// The shell's place in the file system: the home folder (~), the working directory, the previous one (for
// cd -), and files that rm, mv and > may not touch, like a file made immutable with chattr +i
class Workspace(val home: File, protectedFiles: Set<File> = emptySet()) {
    private val protectedPaths = protectedFiles.map { normalize(it) }.toSet()

    @Volatile
    var cwd: File = normalize(home)

    @Volatile
    var previous: File? = null

    // ~, ~/path, /path or a path relative to the working directory, with . and .. resolved
    fun resolve(path: String): File = normalize(
        when {
            path == "~" -> home
            path.startsWith("~/") -> File(home, path.drop(2))
            path.startsWith("/") -> File(path)
            else -> File(cwd, path)
        }
    )

    fun isProtected(file: File) = normalize(file) in protectedPaths

    // Whether a protected file is this file or inside this folder
    fun holdsProtected(file: File): Boolean {
        val path = normalize(file).path
        return protectedPaths.any { it.path == path || it.path.startsWith("$path/") }
    }
}

// An absolute path without . and .. parts
internal fun normalize(file: File): File {
    val parts = ArrayDeque<String>()
    for (part in file.absolutePath.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> parts.removeLastOrNull()
            else -> parts.addLast(part)
        }
    }
    return File("/" + parts.joinToString("/"))
}

// The running launcher's workspace, made on first use: home is the shared storage (/storage/emulated/0),
// and the command log is protected. Reading other apps' files there needs Android's "All files access".
object CurrentWorkspace {
    @Volatile
    private var workspace: Workspace? = null

    fun get(context: Context): Workspace = workspace ?: synchronized(this) {
        workspace ?: Workspace(Environment.getExternalStorageDirectory(), setOf(commandLogFile(context)))
            .also { workspace = it }
    }
}
