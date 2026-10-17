package com.ilanmarchand.tui_launcher.commands

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.ilanmarchand.tui_launcher.admin.AdminReceiver

// Reboots the phone. Android lets only the device owner do this, through DevicePolicyManager.
object RebootCommand : BuiltinCommand {
    override val name = "reboot"
    override val synopsis = "reboot"
    override val summary = "Restart the phone."

    override fun run(context: Context, args: String): CommandResult {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return try {
            dpm.reboot(AdminReceiver.component(context))
            CommandResult.Output(emptyList())
        } catch (e: SecurityException) {
            // Not device owner: the same message as reboot run without root on Linux
            error("reboot: Operation not permitted")
        } catch (e: IllegalStateException) {
            // Android refuses while a call is ongoing
            error("reboot: ${e.message}")
        }
    }

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}
