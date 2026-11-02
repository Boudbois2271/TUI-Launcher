// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeBrightnessTest {
    @Test
    fun absoluteVolumeLandsOnTheNearestStep() {
        assertEquals(8, volumeStep("0.5", 2, 15, null))
        assertEquals(8, volumeStep("50%", 2, 15, null))
        assertEquals(15, volumeStep("1.0", 2, 15, null))
        assertEquals(0, volumeStep("0", 7, 15, null))
        // Android can't go above its top step, even when wpctl could amplify
        assertEquals(15, volumeStep("1.5", 2, 15, null))
    }

    @Test
    fun relativeVolumeMovesAtLeastOneStep() {
        assertEquals(5, volumeStep("5%+", 4, 15, null))
        assertEquals(3, volumeStep("5%-", 4, 15, null))
        assertEquals(5, volumeStep("1%+", 4, 15, null))
        assertEquals(10, volumeStep("0.4+", 4, 15, null))
        assertEquals(15, volumeStep("5%+", 15, 15, null))
        assertEquals(0, volumeStep("5%-", 0, 15, null))
        assertEquals(4, volumeStep("0%+", 4, 15, null))
    }

    @Test
    fun limitCapsTheVolume() {
        assertEquals(15, volumeStep("5%+", 15, 15, 1.0))
        assertEquals(7, volumeStep("5%+", 7, 15, 0.5))
        assertEquals(7, volumeStep("1.0", 2, 15, 0.5))
    }

    @Test
    fun invalidVolume() {
        assertNull(volumeStep("abc", 4, 15, null))
        assertNull(volumeStep("+5%", 4, 15, null))
        assertNull(volumeStep("", 4, 15, null))
    }

    @Test
    fun brightnessValues() {
        assertEquals(500, brightnessTarget("500", 1000, 2047))
        assertEquals(1024, brightnessTarget("50%", 1000, 2047))
        assertEquals(1010, brightnessTarget("+10", 1000, 2047))
        assertEquals(950, brightnessTarget("50-", 1000, 2047))
        assertEquals(1205, brightnessTarget("+10%", 1000, 2047))
        assertEquals(1205, brightnessTarget("10%+", 1000, 2047))
        assertEquals(795, brightnessTarget("10%-", 1000, 2047))
    }

    @Test
    fun brightnessStaysInRange() {
        assertEquals(2047, brightnessTarget("200%", 1000, 2047))
        // Like brightnessctl: 0 is allowed, and -n raises the minimum to 1
        assertEquals(0, brightnessTarget("0", 1000, 2047))
        assertEquals(0, brightnessTarget("100%-", 1000, 2047))
        assertEquals(1, brightnessTarget("0", 1000, 2047, min = 1))
    }

    @Test
    fun invalidBrightness() {
        assertNull(brightnessTarget("abc", 1000, 2047))
        assertNull(brightnessTarget("+10-", 1000, 2047))
        assertNull(brightnessTarget("", 1000, 2047))
    }
}
