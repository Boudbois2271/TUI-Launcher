// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.ilanmarchand.tui_launcher.accessibility.LockService

// systemd's loginctl, for locking the screen: `loginctl lock-session` (or `lock-sessions`) locks the phone
// now, through the launcher's accessibility service, or the device admin's force-lock policy when that
// service is off. The other loginctl verbs don't exist on Android.
object LoginctlCommand : BuiltinCommand {
    override val name = "loginctl"
    override val synopsis = "loginctl lock-session"
    override val summary = "Lock the screen."
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
        // Like the power button, so face unlock keeps working
        if (LockService.lockScreen()) return CommandResult.Output(emptyList())
        // Without the accessibility service, the device admin lock still locks, but Android then asks for
        // the PIN at the next unlock
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return try {
            dpm.lockNow()
            error(
                "Locked by the device admin, so the next unlock needs the PIN. " +
                    "Turn on \"TUI-Launcher system actions\" in the Accessibility settings to keep face unlock."
            )
        } catch (e: SecurityException) {
            error("Failed to lock session: Access denied")
        }
    }

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}
