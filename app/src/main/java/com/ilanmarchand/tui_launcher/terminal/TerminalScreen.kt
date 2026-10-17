package com.ilanmarchand.tui_launcher.terminal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import com.ilanmarchand.tui_launcher.apps.searchInApp
import com.ilanmarchand.tui_launcher.apps.splitAppSearch
import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.commands.OutputLine
import com.ilanmarchand.tui_launcher.commands.findBuiltinCommand
import com.ilanmarchand.tui_launcher.commands.shellBuiltinVariables
import com.ilanmarchand.tui_launcher.commands.variableValue
import com.ilanmarchand.tui_launcher.commands.splitShellWords
import com.ilanmarchand.tui_launcher.files.CurrentWorkspace
import com.ilanmarchand.tui_launcher.files.WorkspaceRedirects
import com.ilanmarchand.tui_launcher.log.appendToCommandLog
import com.ilanmarchand.tui_launcher.log.readCommandHistory
import com.ilanmarchand.tui_launcher.shell.CommandRunner
import com.ilanmarchand.tui_launcher.shell.Expander
import com.ilanmarchand.tui_launcher.shell.ShellVariables
import com.ilanmarchand.tui_launcher.shell.assignmentsIn
import com.ilanmarchand.tui_launcher.shell.expandVariables
import com.ilanmarchand.tui_launcher.shell.HistoryExpansion
import com.ilanmarchand.tui_launcher.shell.ParseResult
import com.ilanmarchand.tui_launcher.shell.ShellEffect
import com.ilanmarchand.tui_launcher.shell.expandHistory
import com.ilanmarchand.tui_launcher.shell.parseCommandLine
import com.ilanmarchand.tui_launcher.shell.runCommandList
import com.ilanmarchand.tui_launcher.shell.showResult
import com.ilanmarchand.tui_launcher.system.PROMPT
import com.ilanmarchand.tui_launcher.ui.theme.Accent1
import com.ilanmarchand.tui_launcher.ui.theme.Accent2
import com.ilanmarchand.tui_launcher.ui.theme.TerminalBackground
import com.ilanmarchand.tui_launcher.ui.theme.TerminalForeground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private sealed interface TerminalLine {
    // prompt: the shell's prompt, or the question a command asked
    data class Command(val input: String, val prompt: String = PROMPT) : TerminalLine
    data class Output(val text: String, val isError: Boolean = false, val accentLength: Int = 0) : TerminalLine
}

@Composable
fun TerminalScreen() {
    // Back does nothing: this is the home screen, so it must not be closed
    BackHandler {}

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    // Like any launcher, follow apps being installed, removed or updated (pacman -R, the Play Store…)
    DisposableEffect(Unit) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            fun reload() {
                scope.launch { apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) } }
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { launcherApps.unregisterCallback(callback) }
    }
    val history = remember { mutableStateListOf<TerminalLine>() }
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Lines run one after another, as in bash, even when typed while a command is still running
    val running = remember { Mutex() }
    val variables = ShellVariables.variables
    // A question a command asked; the next typed line answers it
    var question by remember { mutableStateOf<ShellEffect.Ask?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Shows what a command did, on the main thread
    suspend fun show(effect: ShellEffect) = withContext(Dispatchers.Main) {
        when (effect) {
            ShellEffect.ClearScreen -> history.clear()
            is ShellEffect.Print -> history += effect.line.let {
                TerminalLine.Output(it.text, it.isError, it.accentLength)
            }
            is ShellEffect.Ask -> question = effect
        }
    }

    // Runs a typed line: history expansion, logging, parsing, then the commands
    suspend fun runLine(name: String) {
        // Like bash, !! and the other history events are expanded first, and the expanded line is the one
        // echoed, logged and run, so the log stays a usable history
        val past = withContext(Dispatchers.IO) {
            runCatching { readCommandHistory(context) }.getOrDefault(emptyList())
        }
        val expansion = expandHistory(name, past)
        val line = (expansion as? HistoryExpansion.Expanded)?.line ?: name
        val logged = withContext(Dispatchers.IO) {
            runCatching { appendToCommandLog(context, line) }
        }
        if (logged.isFailure) {
            history += TerminalLine.Output("could not write the log", isError = true)
        }
        when (expansion) {
            is HistoryExpansion.Failed -> {
                history += TerminalLine.Output(expansion.message, isError = true)
                return
            }
            is HistoryExpansion.Expanded -> if (expansion.changed) history += TerminalLine.Output(line)
        }
        when (val parsed = parseCommandLine(line)) {
            is ParseResult.SyntaxError -> {
                history += TerminalLine.Output(parsed.message, isError = true)
                // bash's status for a syntax error
                variables.lastStatus = 2
            }
            is ParseResult.Parsed -> withContext(Dispatchers.IO) {
                // Aliases are read once per line, so one defined on a line applies from the next line, as in bash
                val aliases = AliasStore(context).all()
                val workspace = CurrentWorkspace.get(context)
                val expander = object : Expander {
                    fun lookup(name: String, lastStatus: Int) =
                        if (name == "?") lastStatus.toString() else variableValue(name, variables, shellBuiltinVariables(workspace))

                    override fun command(text: String, lastStatus: Int) =
                        expandVariables(expandAliases(text, aliases)) { lookup(it, lastStatus) }

                    override fun target(target: String, lastStatus: Int) =
                        expandVariables(target, escape = false) { lookup(it, lastStatus) }
                }
                val runner = CommandRunner { command, stdin -> runCommand(context, { apps }, command, stdin) }
                variables.lastStatus =
                    runCommandList(parsed.items, expander, runner, WorkspaceRedirects(workspace)) { show(it) }
            }
        }
    }

    fun submit(line: String) {
        val name = line.trim()
        question?.let { asked ->
            question = null
            history += TerminalLine.Command(name, asked.question)
            scope.launch {
                running.withLock {
                    // Like every submitted line, the answer is logged
                    withContext(Dispatchers.IO) { runCatching { appendToCommandLog(context, name) } }
                    val result = withContext(Dispatchers.IO) { asked.answer(name) }
                    showResult(result) { show(it) }?.let { variables.lastStatus = it }
                }
            }
            return
        }
        history += TerminalLine.Command(name)
        if (name.isEmpty()) return
        scope.launch { running.withLock { runLine(name) } }
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
        TerminalKeyboard {
            BasicTextField(
                value = input,
                // The field holds several lines so a long command wraps under the prompt. A new line, from
                // Enter on a hardware keyboard or from pasted text, submits instead: like bash, each pasted
                // line runs as its own command.
                onValueChange = { typed ->
                    val lines = typed.split('\n')
                    lines.dropLast(1).forEach(::submit)
                    input = lines.last()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TerminalForeground),
                cursorBrush = SolidColor(Color.Transparent),
                visualTransformation = remember(question) { promptAndCursor(question?.question ?: PROMPT) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    submit(input)
                    input = ""
                })
            )
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
    // NAME=value words alone set shell variables, as in bash
    assignmentsIn(splitShellWords(command).orEmpty())?.let { assignments ->
        assignments.forEach { (name, value) -> ShellVariables.variables.set(name, value) }
        return CommandResult.Output(emptyList())
    }
    findBuiltinCommand(command)?.let { return it.command.run(context, it.args, stdin) }
    val loaded = snapshotFlow(apps).filterNotNull().first()
    // A label with a shell character, such as "Red & moi", is typed in quotes
    val app = loaded.findByName(command)
        ?: splitShellWords(command)?.joinToString(" ")?.let { loaded.findByName(it) }
        ?: return searchApp(context, loaded, command)
    return try {
        app.launch(context)
        CommandResult.Output(emptyList())
    } catch (e: ActivityNotFoundException) {
        CommandResult.Output(listOf(OutputLine("could not open $command", isError = true)))
    }
}

// `<app> -s QUERY` searches inside the app; any other unknown line is bash's "command not found"
private fun searchApp(context: Context, apps: List<LaunchableApp>, command: String): CommandResult {
    val notFound = CommandResult.Output(listOf(OutputLine("command not found: $command", isError = true)), exitCode = 127)
    val (label, query) = splitAppSearch(command) ?: return notFound
    val app = apps.findByName(label) ?: return notFound
    if (searchInApp(context, app, query)) return CommandResult.Output(emptyList())
    return CommandResult.Output(listOf(OutputLine("${app.label}: this app offers no search to other apps", isError = true)))
}

@Composable
private fun TerminalLineText(line: TerminalLine) {
    when (line) {
        is TerminalLine.Command -> Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Accent1)) { append("${line.prompt} ") }
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

// The prompt, drawn inside the field so a long command wraps onto the next line from the left edge, like
// in a real terminal, then the typed text and a static block cursor. The field's own cursor is hidden
// because it blinks, and there are no animations yet.
private fun promptAndCursor(promptText: String) = VisualTransformation { text ->
    val prompt = "$promptText "
    TransformedText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Accent1)) { append(prompt) }
            append(text)
            withStyle(SpanStyle(background = TerminalForeground, color = TerminalBackground)) {
                append(" ")
            }
        },
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + prompt.length
            override fun transformedToOriginal(offset: Int) = (offset - prompt.length).coerceIn(0, text.length)
        }
    )
}
