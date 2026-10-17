package com.ilanmarchand.tui_launcher.commands

import android.Manifest
import android.content.Context
import com.ilanmarchand.tui_launcher.admin.grantOwnPermission
import com.ilanmarchand.tui_launcher.contacts.addContact
import com.ilanmarchand.tui_launcher.contacts.isPhoneNumber
import com.ilanmarchand.tui_launcher.contacts.readContactNumbers

// The phone's address book. Linux has no standard command for it, so the syntax is the launcher's own:
//   contacts list
//   contacts add NAME NUMBER     (quote a name with spaces: contacts add 'Jane DOE' 0612345678)
// `contacts` alone still opens the Contacts app. New contacts are saved on the phone itself, outside the
// Google account.
object ContactsCommand : BuiltinCommand {
    override val name = "contacts"
    override val synopsis = "contacts list | add NAME NUMBER"
    override val summary = "List the address book or add a contact. Alone, opens the Contacts app."
    override val takesArgs = true
    override val needsArgs = true

    private val USAGE = listOf("usage: contacts list", "       contacts add NAME NUMBER")

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args)
            ?: return error("contacts: unexpected EOF while looking for matching quote")
        return when (words.firstOrNull()) {
            "list" -> list(context, words.drop(1))
            "add" -> add(context, words.drop(1))
            else -> usage()
        }
    }

    private fun list(context: Context, args: List<String>): CommandResult {
        if (args.isNotEmpty()) return usage()
        if (!grantOwnPermission(context, Manifest.permission.READ_CONTACTS)) return error("contacts: Permission denied")
        val rows = readContactNumbers(context).map { listOf(it.name, it.number) }
        if (rows.isEmpty()) return CommandResult.Output(emptyList())
        return CommandResult.Output(formatTable(listOf("NAME", "NUMBER"), rows).map { OutputLine(it) })
    }

    private fun add(context: Context, args: List<String>): CommandResult {
        if (args.size != 2) return usage()
        val (contactName, number) = args
        if (!isPhoneNumber(number)) return error("contacts: '$number' is not a phone number")
        if (!grantOwnPermission(context, Manifest.permission.WRITE_CONTACTS)) return error("contacts: Permission denied")
        addContact(context, contactName, number)
        return CommandResult.Output(emptyList())
    }

    private fun usage() = CommandResult.Output(USAGE.map { OutputLine(it, isError = true) })

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}
