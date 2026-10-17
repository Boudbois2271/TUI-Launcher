package com.ilanmarchand.tui_launcher.terminal

import android.content.ActivityNotFoundException
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
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
import com.ilanmarchand.tui_launcher.commands.OutputLine
import com.ilanmarchand.tui_launcher.commands.findBuiltinCommand
import com.ilanmarchand.tui_launcher.commands.splitShellWords
import com.ilanmarchand.tui_launcher.log.appendToCommandLog
import com.ilanmarchand.tui_launcher.shell.CommandRunner
import com.ilanmarchand.tui_launcher.shell.ParseResult
import com.ilanmarchand.tui_launcher.shell.ShellEffect
import com.ilanmarchand.tui_launcher.shell.parseCommandLine
import com.ilanmarchand.tui_launcher.shell.runCommandList
import com.ilanmarchand.tui_launcher.system.PROMPT
import com.ilanmarchand.tui_launcher.ui.theme.Accent1
import com.ilanmarchand.tui_launcher.ui.theme.Accent2
import com.ilanmarchand.tui_launcher.ui.theme.TerminalBackground
import com.ilanmarchand.tui_launcher.ui.theme.TerminalForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface TerminalLine {
    data class Command(val input: String) : TerminalLine
    data class Output(val text: String, val isError: Boolean = false, val accentLength: Int = 0) : TerminalLine
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

        scope.launch {
            val logged = withContext(Dispatchers.IO) {
                runCatching { appendToCommandLog(context, name) }
            }
            if (logged.isFailure) {
                history += TerminalLine.Output("could not write the log", isError = true)
            }
            when (val parsed = parseCommandLine(name)) {
                is ParseResult.SyntaxError -> history += TerminalLine.Output(parsed.message, isError = true)
                is ParseResult.Parsed -> withContext(Dispatchers.IO) {
                    // Aliases are read once per line, so one defined on a line applies from the next line,
                    // as in bash. The log keeps the line as typed.
                    val aliases = AliasStore(context).all()
                    val runner = CommandRunner { command, stdin -> runCommand(context, { apps }, command, stdin) }
                    runCommandList(parsed.items, { expandAliases(it, aliases) }, runner) { effect ->
                        withContext(Dispatchers.Main) {
                            when (effect) {
                                ShellEffect.ClearScreen -> history.clear()
                                is ShellEffect.Print -> history += effect.line.let {
                                    TerminalLine.Output(it.text, it.isError, it.accentLength)
                                }
                            }
                        }
                    }
                }
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
            TerminalKeyboard {
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
}

// Tells the keyboard this is a terminal, the way Termux does: a visible-password field with no
// suggestions, which keyboards such as Gboard never autocorrect (they ignore autoCorrectEnabled = false
// on a plain text field). The keyboard is also asked not to learn what is typed, such as Wi-Fi passwords.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TerminalKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val terminalRequest = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    outAttributes.imeOptions = outAttributes.imeOptions or
                        EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            nextHandler.startInputMethod(terminalRequest)
        },
        content = content
    )
}

// Runs one command of a line: a built-in, the temporary `help launch`, or an app. An app name waits for the
// app list if it is still loading, so no command is refused. Like bash, an unknown command exits with 127.
private suspend fun runCommand(
    context: Context,
    apps: () -> List<LaunchableApp>?,
    command: String,
    stdin: List<String>?
): CommandResult {
    // Temporary: lists every launchable app's label, to check the names to type. Checked before the
    // built-ins, since `help` is one.
    if (command.equals("help launch", ignoreCase = true)) {
        val loaded = snapshotFlow(apps).filterNotNull().first()
        return CommandResult.Output((listOf("apps you can launch:") + loaded.map { it.label }).map { OutputLine(it) })
    }
    findBuiltinCommand(command)?.let { return it.command.run(context, it.args, stdin) }
    val loaded = snapshotFlow(apps).filterNotNull().first()
    // A label with a shell character, such as "Red & moi", is typed in quotes
    val app = loaded.findByName(command)
        ?: splitShellWords(command)?.joinToString(" ")?.let { loaded.findByName(it) }
        ?: return CommandResult.Output(listOf(OutputLine("command not found: $command", isError = true)), exitCode = 127)
    return try {
        app.launch(context)
        CommandResult.Output(emptyList())
    } catch (e: ActivityNotFoundException) {
        CommandResult.Output(listOf(OutputLine("could not open $command", isError = true)))
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
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Accent1)) { append(line.text.take(line.accentLength)) }
                append(line.text.drop(line.accentLength))
            },
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
