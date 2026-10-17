package com.ilanmarchand.tui_launcher.terminal

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ilanmarchand.tui_launcher.aliases.AliasStore
import com.ilanmarchand.tui_launcher.aliases.expandAliases
import com.ilanmarchand.tui_launcher.apps.LaunchableApp
import com.ilanmarchand.tui_launcher.apps.findByName
import com.ilanmarchand.tui_launcher.apps.launch
import com.ilanmarchand.tui_launcher.apps.loadLaunchableApps
import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.findBuiltinCommand
import com.ilanmarchand.tui_launcher.log.appendToCommandLog
import com.ilanmarchand.tui_launcher.ui.theme.Accent1
import com.ilanmarchand.tui_launcher.ui.theme.Accent2
import com.ilanmarchand.tui_launcher.ui.theme.TerminalBackground
import com.ilanmarchand.tui_launcher.ui.theme.TerminalForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PROMPT = "Boudbois@phone: >"

private sealed interface TerminalLine {
    data class Command(val input: String) : TerminalLine
    data class Output(val text: String, val isError: Boolean = false) : TerminalLine
}

@Composable
fun TerminalScreen() {
    // Back does nothing: this is the home screen, so it must not be closed
    BackHandler {}

    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    val history = remember { mutableStateListOf<TerminalLine>() }
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submit() {
        val name = input.trim()
        input = ""
        history += TerminalLine.Command(name)
        if (name.isEmpty()) return

        // An app name waits for the app list if it is still loading, so a command is never refused
        scope.launch {
            val logged = withContext(Dispatchers.IO) {
                runCatching { appendToCommandLog(context, name) }
            }
            if (logged.isFailure) {
                history += TerminalLine.Output("could not write the log", isError = true)
            }
            // Aliases rewrite the line before anything else reads it, like in a shell.
            // The log keeps the line as typed.
            val line = withContext(Dispatchers.IO) { expandAliases(name, AliasStore(context).all()) }
            val builtin = findBuiltinCommand(line)
            if (builtin != null) {
                when (val result = withContext(Dispatchers.IO) { builtin.command.run(context, builtin.args) }) {
                    CommandResult.Clear -> history.clear()
                    is CommandResult.Output -> result.lines.forEach {
                        history += TerminalLine.Output(it.text, it.isError)
                    }
                }
                return@launch
            }
            val loaded = snapshotFlow { apps }.filterNotNull().first()
            // Temporary: lists every launchable app's label, to check the names to type
            if (line.equals("help launch", ignoreCase = true)) {
                history += TerminalLine.Output("apps you can launch:")
                loaded.forEach { history += TerminalLine.Output(it.label) }
                return@launch
            }
            val app = loaded.findByName(line)
            if (app == null) {
                history += TerminalLine.Output("command not found: $line", isError = true)
                return@launch
            }
            try {
                app.launch(context)
            } catch (e: ActivityNotFoundException) {
                history += TerminalLine.Output("could not open $line", isError = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(history.asReversed()) { line ->
                TerminalLineText(line)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "$PROMPT ", color = Accent1)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TerminalForeground),
                cursorBrush = SolidColor(Color.Transparent),
                visualTransformation = BlockCursor,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
        }
    }
}

@Composable
private fun TerminalLineText(line: TerminalLine) {
    when (line) {
        is TerminalLine.Command -> Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Accent1)) { append("$PROMPT ") }
                append(line.input)
            },
            color = TerminalForeground
        )

        is TerminalLine.Output -> Text(
            text = line.text,
            color = if (line.isError) Accent2 else TerminalForeground
        )
    }
}

// Static block cursor drawn after the typed text. The field's own cursor is hidden
// because it blinks, and there are no animations yet.
private val BlockCursor = VisualTransformation { text ->
    TransformedText(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(background = TerminalForeground, color = TerminalBackground)) {
                append(" ")
            }
        },
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(text.length)
        }
    )
}
