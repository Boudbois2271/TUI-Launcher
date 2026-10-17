package com.ilanmarchand.tui_launcher.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmcliTest {
    @Test
    fun prefixesPickTheFirstKeywordInNmcliOrder() {
        val objects = listOf("general", "networking", "radio", "connection", "device", "agent", "monitor")
        assertEquals("device", matchArgument("d", objects))
        assertEquals("radio", matchArgument("r", objects))
        assertEquals("connection", matchArgument("c", objects))
        assertEquals("device", matchArgument("device", objects))
        assertNull(matchArgument("x", objects))
        assertNull(matchArgument("", objects))
        assertEquals("status", matchArgument("s", listOf("status", "show", "set")))
        assertEquals("show", matchArgument("sh", listOf("status", "show", "set")))
    }

    @Test
    fun signalAndBars() {
        assertEquals(0, signalPercent(-110))
        assertEquals(60, signalPercent(-70))
        assertEquals(100, signalPercent(-40))
        assertEquals("▂▄▆█", signalBars(90))
        assertEquals("▂▄▆_", signalBars(60))
        assertEquals("▂▄__", signalBars(31))
        assertEquals("▂___", signalBars(6))
        assertEquals("____", signalBars(5))
    }

    @Test
    fun channels() {
        assertEquals(1, channelOf(2412))
        assertEquals(13, channelOf(2472))
        assertEquals(14, channelOf(2484))
        assertEquals(36, channelOf(5180))
        assertEquals(1, channelOf(5955))
        assertNull(channelOf(1000))
    }

    @Test
    fun securityColumn() {
        assertEquals("--", securityOf("[ESS]"))
        assertEquals("WPA2", securityOf("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals("WPA1 WPA2", securityOf("[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][ESS]"))
        assertEquals("WPA2 WPA3", securityOf("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals("WPA3", securityOf("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA2 802.1X", securityOf("[WPA2-EAP-CCMP][ESS]"))
        assertEquals("WEP", securityOf("[WEP][ESS]"))
    }

    @Test
    fun howToJoin() {
        assertEquals(ConnectSecurity.OPEN, connectSecurity("[ESS]"))
        assertEquals(ConnectSecurity.PSK, connectSecurity("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(ConnectSecurity.SAE, connectSecurity("[RSN-SAE-CCMP][ESS]"))
        assertEquals(ConnectSecurity.UNSUPPORTED, connectSecurity("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(ConnectSecurity.UNSUPPORTED, connectSecurity("[WEP][ESS]"))
    }

    @Test
    fun tableColumnsAreAligned() {
        assertEquals(
            listOf("IN-USE  SSID", "*       home", "        cafe-wifi"),
            formatTable(listOf("IN-USE", "SSID"), listOf(listOf("*", "home"), listOf("", "cafe-wifi")))
        )
    }
}
