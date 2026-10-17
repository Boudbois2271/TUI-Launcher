package com.ilanmarchand.tui_launcher.commands

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCommandsTest {
    private var screen = 1024
    private var flash = 0
    private val devices = listOf(
        BrightnessDevice("lcd-backlight", 2047, { screen }, { screen = it; true }),
        BrightnessDevice("white:flash", 1, { flash }, { flash = it; true })
    )

    private fun out(vararg lines: String, exitCode: Int = 0) = CommandResult.Output(lines.map { OutputLine(it) }, exitCode)
    private fun err(vararg lines: String) = CommandResult.Output(lines.map { OutputLine(it, isError = true) })

    @Test
    fun theScreenIsTheDefaultDevice() {
        assertEquals(out("1024"), brightnessctl(listOf("get"), devices))
        assertEquals(
            out("Device 'lcd-backlight' of class 'leds':", "\tCurrent brightness: 1024 (50%)", "\tMax brightness: 2047"),
            brightnessctl(emptyList(), devices)
        )
    }

    @Test
    fun theFlashIsATorch() {
        assertEquals(
            out("Updated device 'white:flash':", "Device 'white:flash' of class 'leds':", "\tCurrent brightness: 1 (100%)", "\tMax brightness: 1"),
            brightnessctl(listOf("-d", "white:flash", "set", "1"), devices)
        )
        assertEquals(1, flash)
        assertEquals(out(), brightnessctl(listOf("-q", "--device=*flash", "s", "0"), devices))
        assertEquals(0, flash)
        assertEquals(1024, screen)
    }

    @Test
    fun listAndErrors() {
        assertEquals(
            out(
                "Available devices:",
                "Device 'lcd-backlight' of class 'leds':", "\tCurrent brightness: 1024 (50%)", "\tMax brightness: 2047",
                "",
                "Device 'white:flash' of class 'leds':", "\tCurrent brightness: 0 (0%)", "\tMax brightness: 1"
            ),
            brightnessctl(listOf("-l"), devices)
        )
        assertEquals(err("Device 'nosuch' not found."), brightnessctl(listOf("-d", "nosuch", "info"), devices))
        assertEquals(err("Invalid value given"), brightnessctl(listOf("set", "abc"), devices))
        assertEquals(err("You need to provide a value to set."), brightnessctl(listOf("set"), devices))
    }

    @Test
    fun pretendDoesNotWrite() {
        brightnessctl(listOf("-p", "set", "10"), devices)
        assertEquals(1024, screen)
    }

    @Test
    fun playerctlSendsMediaKeys() {
        val pressed = mutableListOf<Int>()
        assertEquals(out(), PlayerctlCommand.playerctl(listOf("play-pause"), false) { pressed += it })
        assertEquals(out(), PlayerctlCommand.playerctl(listOf("next"), false) { pressed += it })
        assertEquals(listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT), pressed)
        assertEquals(out("Playing"), PlayerctlCommand.playerctl(listOf("status"), true) {})
        assertEquals(err("playerctl: Command not recognized: foo"), PlayerctlCommand.playerctl(listOf("foo"), false) {})
    }
}
