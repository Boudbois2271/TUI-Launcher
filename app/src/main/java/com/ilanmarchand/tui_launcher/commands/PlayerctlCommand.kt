package com.ilanmarchand.tui_launcher.commands

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

// playerctl, for the media player: play, pause, play-pause, stop, next and previous send the same media keys
// as a headset, to whichever app played last. Android shows other apps' media details only to an app with
// notification access, so status can only tell whether something is playing.
object PlayerctlCommand : BuiltinCommand {
    override val name = "playerctl"
    override val synopsis = "playerctl play | pause | play-pause | stop | next | previous | status"
    override val summary = "Control the media player, like playerctl."
    override val takesArgs = true

    private val KEYS = mapOf(
        "play" to KeyEvent.KEYCODE_MEDIA_PLAY,
        "pause" to KeyEvent.KEYCODE_MEDIA_PAUSE,
        "play-pause" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        "stop" to KeyEvent.KEYCODE_MEDIA_STOP,
        "next" to KeyEvent.KEYCODE_MEDIA_NEXT,
        "previous" to KeyEvent.KEYCODE_MEDIA_PREVIOUS
    )

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return errorLines("playerctl: unexpected EOF while looking for matching quote")
        val audio = context.getSystemService(AudioManager::class.java)
        return playerctl(words, audio.isMusicActive) { key ->
            val time = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0))
        }
    }

    internal fun playerctl(words: List<String>, playing: Boolean, press: (Int) -> Unit): CommandResult {
        val command = words.firstOrNull() ?: return errorLines("playerctl: no command given", "Try 'playerctl --help' for more information.")
        if (command == "-h" || command == "--help") return textLines(HELP)
        KEYS[command]?.let { key ->
            press(key)
            return CommandResult.Output(emptyList())
        }
        return when (command) {
            "status" -> textLines(listOf(if (playing) "Playing" else "Stopped"))
            "position", "volume", "metadata", "open", "loop", "shuffle" ->
                errorLines("playerctl: '$command' is not available on this phone")
            else -> errorLines("playerctl: Command not recognized: $command")
        }
    }

    private val HELP = listOf(
        "Usage:",
        "  playerctl [OPTION…] COMMAND - Controller for media players",
        "",
        "Available Commands:",
        "  play                    Command the player to play",
        "  pause                   Command the player to pause",
        "  play-pause              Command the player to toggle between play/pause",
        "  stop                    Command the player to stop",
        "  next                    Command the player to skip to the next track",
        "  previous                Command the player to skip to the previous track",
        "  status                  Get the play status of the player"
    )
}
