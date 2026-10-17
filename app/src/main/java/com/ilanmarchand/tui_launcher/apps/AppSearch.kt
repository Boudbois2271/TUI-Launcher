package com.ilanmarchand.tui_launcher.apps

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ilanmarchand.tui_launcher.commands.splitShellWords

// `<app> -s QUERY` searches inside an app: `youtube -s lofi`, `duckduckgo -s kotlin flows`. Returns the app
// name before -s and the query after it, or null when the line isn't that form. It is the one exception to
// "extra words after an app name make the line unknown".
fun splitAppSearch(command: String): Pair<String, String>? {
    val words = splitShellWords(command) ?: return null
    val index = words.indexOf("-s")
    if (index < 1 || index == words.lastIndex) return null
    return words.take(index).joinToString(" ") to words.drop(index + 1).joinToString(" ")
}

// Opens the app's own search screen if it has one (YouTube), else its web search (a browser such as
// DuckDuckGo). False when the app offers neither to other apps. A search screen counts only when it
// declares android.app.searchable, as Android's search framework requires: MIUI's Camera answers
// ACTION_SEARCH with its settings screen, which is no search.
fun searchInApp(context: Context, app: LaunchableApp, query: String): Boolean {
    val search = Intent(Intent.ACTION_SEARCH).setPackage(app.packageName)
    val searchScreen = context.packageManager.queryIntentActivities(search, PackageManager.GET_META_DATA)
        .firstOrNull { it.activityInfo.metaData?.containsKey(SEARCHABLE) == true }
    val intents = listOfNotNull(
        searchScreen?.let { search.setClassName(it.activityInfo.packageName, it.activityInfo.name) },
        Intent(Intent.ACTION_WEB_SEARCH).setPackage(app.packageName)
    )
    for (intent in intents) {
        intent.putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            // Try the next kind of search
        }
    }
    return false
}

private const val SEARCHABLE = "android.app.searchable"
