// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSearchTest {
    @Test
    fun appNameThenQuery() {
        assertEquals("youtube" to "lofi hip hop", splitAppSearch("youtube -s lofi hip hop"))
        assertEquals("jw library" to "psalm", splitAppSearch("jw library -s psalm"))
        assertEquals("duckduckgo" to "a b", splitAppSearch("duckduckgo -s 'a b'"))
    }

    @Test
    fun notASearch() {
        assertNull(splitAppSearch("youtube"))
        assertNull(splitAppSearch("youtube -s"))
        assertNull(splitAppSearch("-s query"))
        assertNull(splitAppSearch("instagram test"))
    }
}
