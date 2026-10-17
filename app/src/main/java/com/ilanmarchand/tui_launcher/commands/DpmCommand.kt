package com.ilanmarchand.tui_launcher.commands

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import com.ilanmarchand.tui_launcher.admin.AdminReceiver

// Android's dpm tool, limited to what the launcher can do to itself: remove-active-admin gives up
// device owner and the admin. Usage lines and messages follow the real dpm of Android 11.
object DpmCommand : BuiltinCommand {
    override val name = "dpm"
    override val takesArgs = true

    // Android user ids are uid / 100000
    private const val PER_USER_RANGE = 100_000

    private val USAGE = listOf(
        "usage: dpm [subcommand] [options]",
        "usage: dpm remove-active-admin [ --user <USER_ID> | current ] [ --name <NAME> ] <COMPONENT>",
        "",
        "dpm remove-active-admin: Disables an active admin. This will also remove device and profile " +
            "owners. Only this launcher's own admin can be removed from here."
    )

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return error("dpm: unexpected EOF while looking for matching quote")
        val subcommand = words.firstOrNull() ?: return usage()
        if (subcommand != "remove-active-admin") return usage("Error: unknown command '$subcommand'")

        var index = 1
        while (index < words.size && words[index].startsWith("--")) {
            val option = words[index++]
            if (option != "--user") return usage("Error: Unknown option: $option")
            val user = words.getOrNull(index++) ?: return usage("Error: Argument expected after \"--user\"")
            val currentUser = Process.myUid() / PER_USER_RANGE
            if (user != "current" && user != "cur" && user.toIntOrNull() != currentUser) {
                return error("Error: only user $currentUser can be changed from here")
            }
        }
        val text = words.getOrNull(index) ?: return usage("Error: Must provide a component name")
        val component = ComponentName.unflattenFromString(text) ?: return usage("Error: Invalid component $text")
        val ours = AdminReceiver.component(context)
        if (component != ours) return error("Error: only ${ours.flattenToShortString()} can be removed from here")

        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isAdminActive(ours)) return error("Error: $ours is not an active admin")
        return try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // Give the home screen choice back first, so another launcher can be picked again
                dpm.clearPackagePersistentPreferredActivities(ours, context.packageName)
                // Deprecated, but still the way for a device owner to step down by itself
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(context.packageName)
            }
            if (dpm.isAdminActive(ours)) dpm.removeActiveAdmin(ours)
            CommandResult.Output(listOf(OutputLine("Success: Admin removed $ours")))
        } catch (e: SecurityException) {
            error("Error: $e")
        }
    }

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))

    // Like the real dpm, a usage error prints the usage, a blank line, then the error
    private fun usage(error: String? = null) = CommandResult.Output(
        USAGE.map { OutputLine(it) } +
            listOfNotNull(error).flatMap { listOf(OutputLine(""), OutputLine(it, isError = true)) }
    )
}
