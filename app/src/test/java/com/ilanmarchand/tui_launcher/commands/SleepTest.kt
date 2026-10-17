package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Test

// Messages were checked against GNU sleep on the PC
class SleepTest {
    @Test
    fun durationsAddUp() {
        assertEquals(SleepDuration.Millis(5_000), sleepDuration(listOf("5")))
        assertEquals(SleepDuration.Millis(90_000), sleepDuration(listOf("1m", "30s")))
        assertEquals(SleepDuration.Millis(500), sleepDuration(listOf("0.5")))
        assertEquals(SleepDuration.Millis(3_600_000), sleepDuration(listOf("1h")))
        assertEquals(SleepDuration.Millis(200), sleepDuration(listOf(".1", ".1")))
    }

    @Test
    fun badIntervals() {
        assertEquals(
            SleepDuration.Invalid(errorLines("sleep: missing operand", "Try 'sleep --help' for more information.")),
            sleepDuration(emptyList())
        )
        assertEquals(
            SleepDuration.Invalid(errorLines("sleep: invalid time interval ‘1q’", "Try 'sleep --help' for more information.")),
            sleepDuration(listOf("1q"))
        )
        assertEquals(
            SleepDuration.Invalid(errorLines("sleep: invalid option -- '5'", "Try 'sleep --help' for more information.")),
            sleepDuration(listOf("-5"))
        )
    }
}
