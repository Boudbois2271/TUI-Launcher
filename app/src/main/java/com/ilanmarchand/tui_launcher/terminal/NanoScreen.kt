package com.ilanmarchand.tui_launcher.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ilanmarchand.tui_launcher.commands.CommandResult
import com.ilanmarchand.tui_launcher.files.nanoLineCount
import com.ilanmarchand.tui_launcher.files.nanoLines
import com.ilanmarchand.tui_launcher.ui.theme.TerminalBackground
import com.ilanmarchand.tui_launcher.ui.theme.TerminalForeground
import kotlinx.coroutines.launch

// nano's screen: the reversed title bar (version, file name, "Modified"), the text, the status line, and the
// bottom bar, whose ^O Write Out and ^X Exit are tapped instead of typed. Leaving with changes asks nano's
// "Save modified buffer?". The cursor is a still block, since there are no animations.
@Composable
fun NanoScreen(edit: CommandResult.Edit, save: suspend (String) -> String?, onExit: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(edit.text)) }
    var saved by remember { mutableStateOf(edit.text) }
    var status by remember {
        mutableStateOf(if (edit.isNew) "[ New File ]" else nanoLines("Read", nanoLineCount(edit.text)))
    }
    var askingToSave by remember { mutableStateOf(false) }
    val modified = value.text != saved
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Saves, then leaves if asked to and the file was written
    fun writeOut(thenExit: Boolean) {
        scope.launch {
            val error = save(value.text)
            if (error == null) {
                saved = value.text
                status = nanoLines("Wrote", nanoLineCount(value.text))
                if (thenExit) onExit()
            } else {
                status = "[ Error writing ${edit.name}: $error ]"
            }
            askingToSave = false
        }
    }

    fun exit() = if (modified) askingToSave = true else onExit()

    BackHandler { exit() }

    Column(
        modifier = Modifier
            // Back leaves nano and never hides the keyboard
            .backKeepsKeyboard { exit() }
            .fillMaxSize()
            .background(TerminalBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalForeground)
                .padding(horizontal = 8.dp)
        ) {
            Text("GNU nano 9.2", color = TerminalBackground, modifier = Modifier.align(Alignment.CenterStart))
            Text(edit.name, color = TerminalBackground, modifier = Modifier.align(Alignment.Center))
            if (modified) Text("Modified", color = TerminalBackground, modifier = Modifier.align(Alignment.CenterEnd))
        }
        TerminalKeyboard(multiLine = true) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    status = ""
                },
                modifier = Modifier
                    .noTextSelection()
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TerminalForeground),
                cursorBrush = SolidColor(Color.Transparent),
                visualTransformation = remember(value.selection.start) { blockCursorAt(value.selection.start) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            if (status.isNotEmpty()) {
                Text(
                    status,
                    color = TerminalBackground,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(TerminalForeground)
                )
            }
        }
        if (askingToSave) {
            Text("Save modified buffer?", color = TerminalForeground, modifier = Modifier.padding(horizontal = 8.dp))
            ShortcutBar(
                listOf(
                    Shortcut("Y", "Yes") { writeOut(thenExit = true) },
                    Shortcut("N", "No", onExit),
                    Shortcut("^C", "Cancel") {
                        askingToSave = false
                        status = "[ Cancelled ]"
                    }
                )
            )
        } else {
            ShortcutBar(
                listOf(
                    Shortcut("^O", "Write Out") { writeOut(thenExit = false) },
                    Shortcut("^X", "Exit") { exit() }
                )
            )
        }
    }
}

private class Shortcut(val key: String, val label: String, val action: () -> Unit)

// nano's bottom bar: each key in reverse video, then what it does. Tapping one runs it; there is no ripple,
// since there are no animations.
@Composable
private fun ShortcutBar(shortcuts: List<Shortcut>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (shortcut in shortcuts) {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = shortcut.action
                )
            ) {
                Text(shortcut.key, color = TerminalBackground, modifier = Modifier.background(TerminalForeground))
                Text(" ${shortcut.label}", color = TerminalForeground)
            }
        }
    }
}

// A still block cursor on the character at `position`, or after the text at its end
private fun blockCursorAt(position: Int) = VisualTransformation { text ->
    val cursor = SpanStyle(background = TerminalForeground, color = TerminalBackground)
    val atEnd = position >= text.length
    TransformedText(
        buildAnnotatedString {
            append(text.text.take(position))
            // At a line end the block sits on a space before the newline, which shifts nothing after it
            val under = text.text.getOrNull(position)
            when {
                atEnd -> withStyle(cursor) { append(" ") }
                under == '\n' -> {
                    withStyle(cursor) { append(" ") }
                    append(text.text.drop(position))
                }
                else -> {
                    withStyle(cursor) { append(under.toString()) }
                    append(text.text.drop(position + 1))
                }
            }
        },
        object : OffsetMapping {
            // The extra space only exists before a newline or at the end, right where the cursor is
            private val extra = atEnd || text.text.getOrNull(position) == '\n'
            override fun originalToTransformed(offset: Int) = if (extra && offset > position) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = when {
                !extra -> offset.coerceAtMost(text.length)
                offset > position -> (offset - 1).coerceAtMost(text.length)
                else -> offset
            }
        }
    )
}
