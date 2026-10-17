package com.ilanmarchand.tui_launcher.commands

import android.app.admin.DevicePolicyManager
import android.content.Context

// systemd's loginctl, for locking the screen: `loginctl lock-session` (or `lock-sessions`) locks the phone
// now, through the device admin's force-lock policy. The other loginctl verbs don't exist on Android.
object LoginctlCommand : BuiltinCommand {
    override val name = "loginctl"
    override val takesArgs = true

    private val VERBS = listOf(
        "list-sessions", "session-status", "show-session", "activate", "lock-session", "unlock-session",
        "lock-sessions", "unlock-sessions", "terminate-session", "kill-session", "list-users", "user-status",
        "show-user", "enable-linger", "disable-linger", "terminate-user", "kill-user", "list-seats",
        "seat-status", "show-seat", "attach", "flush-devices", "terminate-seat"
    )

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args)
            ?: return error("loginctl: unexpected EOF while looking for matching quote")
        val verb = words.firstOrNull()
            ?: return error("'loginctl' alone is not available on this phone. Try 'loginctl lock-session'.")
        if (verb !in VERBS) return error("Unknown command verb '$verb'.")
        if (verb != "lock-session" && verb != "lock-sessions") {
            return error("'loginctl $verb' is not available on this phone.")
        }
        // The phone has a single session, so no session id is taken
        if (words.size > 1) return error("Too many arguments.")
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return try {
            dpm.lockNow()
            CommandResult.Output(emptyList())
        } catch (e: SecurityException) {
            error("Failed to lock session: Access denied")
        }
    }

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}
