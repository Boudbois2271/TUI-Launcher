// SPDX-License-Identifier: GPL-3.0-or-later

package com.ilanmarchand.tui_launcher.commands

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import com.ilanmarchand.tui_launcher.admin.grantOwnPermission

// NetworkManager's nmcli, for the Wi-Fi parts Android lets a device owner do:
//   nmcli radio wifi [on | off]
//   nmcli device wifi [list]
//   nmcli device wifi connect <SSID> [password <password>]
// Objects and commands can be shortened to any prefix, as in nmcli (`nmcli r wifi off`, `nmcli d w l`).
// Since Android 10, only a device owner may switch Wi-Fi or add networks.
object NmcliCommand : BuiltinCommand {
    override val name = "nmcli"
    override val synopsis = "nmcli radio wifi [on | off] | device wifi [list | connect <SSID> [password <pw>]]"
    override val summary = "Switch Wi-Fi, list networks and join one, like NetworkManager's nmcli."
    override val takesArgs = true

    // Android names its Wi-Fi interface wlan0
    private const val INTERFACE = "wlan0"
    private const val CONNECT_TIMEOUT_SECONDS = 30

    private val USAGE = listOf(
        "Usage: nmcli [OPTIONS] OBJECT { COMMAND | help }",
        "  nmcli radio wifi [on | off]",
        "  nmcli device wifi [list]",
        "  nmcli device wifi connect <SSID> [password <password>]"
    )

    // nmcli's own order, so a prefix picks the same word as in nmcli
    private val OBJECTS = listOf("general", "networking", "radio", "connection", "device", "agent", "monitor")
    private val RADIO_TYPES = listOf("all", "wifi", "wwan")
    private val DEVICE_COMMANDS = listOf(
        "status", "show", "set", "connect", "reapply", "modify", "disconnect", "delete", "monitor", "wifi", "lldp"
    )
    private val WIFI_COMMANDS = listOf("list", "connect", "hotspot", "rescan", "show-password")

    override fun run(context: Context, args: String): CommandResult {
        val words = splitShellWords(args) ?: return error("nmcli: unexpected EOF while looking for matching quote")
        val word = words.firstOrNull() ?: return CommandResult.Output(USAGE.map { OutputLine(it) })
        return when (val obj = matchArgument(word, OBJECTS)) {
            null -> error("Error: argument '$word' not understood. Try passing --help instead.")
            "radio" -> radio(context, words.drop(1))
            "device" -> device(context, words.drop(1))
            else -> error("Error: 'nmcli $obj' is not available on this phone.")
        }
    }

    private fun radio(context: Context, args: List<String>): CommandResult {
        val word = args.firstOrNull()
            ?: return error("Error: 'nmcli radio' is not available on this phone. Try 'nmcli radio wifi'.")
        when (val type = matchArgument(word, RADIO_TYPES)) {
            "wifi" -> Unit
            null -> return error("Error: 'radio' command '$word' is not valid.")
            else -> return error("Error: 'nmcli radio $type' is not available on this phone.")
        }
        val wifi = wifiManager(context)
        val state = args.getOrNull(1) ?: return print(if (wifi.isWifiEnabled) "enabled" else "disabled")
        val enable = when (state) {
            "on" -> true
            "off" -> false
            else -> return error("Error: invalid 'wifi' argument: '$state'.")
        }
        if (args.size > 2) return error("Error: invalid extra argument '${args[2]}'.")
        @Suppress("DEPRECATION")
        val accepted = wifi.setWifiEnabled(enable)
        return if (accepted) CommandResult.Output(emptyList()) else error("Error: Not authorized to control networking.")
    }

    private fun device(context: Context, args: List<String>): CommandResult {
        val word = args.firstOrNull()
            ?: return error("Error: 'nmcli device' is not available on this phone. Try 'nmcli device wifi'.")
        val command = matchArgument(word, DEVICE_COMMANDS)
            ?: return error("Error: 'device' command '$word' is not valid.")
        if (command != "wifi") return error("Error: 'nmcli device $command' is not available on this phone.")
        val rest = args.drop(1)
        val wifiWord = rest.firstOrNull() ?: return list(context, emptyList())
        return when (val wifiCommand = matchArgument(wifiWord, WIFI_COMMANDS)) {
            null -> error("Error: 'device wifi' command '$wifiWord' is not valid.")
            "list" -> list(context, rest.drop(1))
            "connect" -> connect(context, rest.drop(1))
            else -> error("Error: 'nmcli device wifi $wifiCommand' is not available on this phone.")
        }
    }

    // locationProblem() grants the location permission before any Wi-Fi network is read
    @SuppressLint("MissingPermission")
    private fun list(context: Context, args: List<String>): CommandResult {
        if (args.isNotEmpty()) return error("Error: invalid extra argument '${args[0]}'.")
        locationProblem(context)?.let { return error(it) }
        val wifi = wifiManager(context)
        // Android throttles scans, so the list may come from the previous scan, a few seconds old
        @Suppress("DEPRECATION")
        wifi.startScan()
        val inUse = currentBssid(wifi)
        val rows = wifi.scanResults
            .sortedWith(compareByDescending<ScanResult> { it.BSSID.uppercase() == inUse }.thenByDescending { it.level })
            .map { result ->
                val signal = signalPercent(result.level)
                listOf(
                    if (result.BSSID.uppercase() == inUse) "*" else "",
                    result.BSSID.uppercase(),
                    ssidOf(result).ifEmpty { "--" },
                    "Infra",
                    channelOf(result.frequency)?.toString() ?: "--",
                    // Android doesn't report the bit rate of a scanned network
                    "--",
                    signal.toString(),
                    signalBars(signal),
                    securityOf(result.capabilities)
                )
            }
        val header = listOf("IN-USE", "BSSID", "SSID", "MODE", "CHAN", "RATE", "SIGNAL", "BARS", "SECURITY")
        return CommandResult.Output(formatTable(header, rows).map { OutputLine(it) })
    }

    // locationProblem() grants the location permission before any Wi-Fi network is read
    @SuppressLint("MissingPermission")
    private fun connect(context: Context, args: List<String>): CommandResult {
        val ssid = args.firstOrNull() ?: return error("Error: SSID or BSSID are missing.")
        var password: String? = null
        var index = 1
        while (index < args.size) {
            val key = args[index]
            if (key != "password") return error("Error: invalid extra argument '$key'.")
            password = args.getOrNull(index + 1) ?: return error("Error: password argument is missing.")
            index += 2
        }
        locationProblem(context)?.let { return error(it) }
        val wifi = wifiManager(context)
        if (!wifi.isWifiEnabled) return error("Error: Wi-Fi is disabled.")

        val quoted = "\"$ssid\""
        @Suppress("DEPRECATION")
        val saved = wifi.configuredNetworks.firstOrNull { it.SSID == quoted }
        val networkId = if (saved != null && password == null) {
            saved.networkId
        } else {
            val seen = wifi.scanResults.firstOrNull { ssidOf(it) == ssid }
                ?: return error("Error: No network with SSID '$ssid' found.")
            val security = connectSecurity(seen.capabilities)
            val config = when (security) {
                ConnectSecurity.UNSUPPORTED ->
                    return error("Error: ${securityOf(seen.capabilities)} networks are not supported here.")
                ConnectSecurity.OPEN -> openNetwork(quoted)
                else -> {
                    val key = password
                        ?: return error("Error: Connection activation failed: (7) Secrets were required, but not provided.")
                    securedNetwork(quoted, key, security)
                        ?: return error("Error: WPA3 networks need Android 11 or newer.")
                }
            }
            @Suppress("DEPRECATION")
            val id = if (saved != null) {
                config.networkId = saved.networkId
                wifi.updateNetwork(config)
            } else {
                wifi.addNetwork(config)
            }
            if (id == -1) return error("Error: Not authorized to control networking.")
            id
        }
        @Suppress("DEPRECATION")
        if (!wifi.enableNetwork(networkId, true)) return error("Error: Not authorized to control networking.")

        // Like nmcli, wait until the connection is up instead of returning at once
        repeat(CONNECT_TIMEOUT_SECONDS * 2) {
            @Suppress("DEPRECATION")
            val info = wifi.connectionInfo
            if (info.networkId == networkId && info.supplicantState == SupplicantState.COMPLETED) {
                return print("Device '$INTERFACE' successfully activated with '$ssid'.")
            }
            Thread.sleep(500)
        }
        return error("Error: Timeout expired ($CONNECT_TIMEOUT_SECONDS seconds)")
    }

    @Suppress("DEPRECATION")
    private fun openNetwork(quotedSsid: String) = WifiConfiguration().apply {
        SSID = quotedSsid
        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
    }

    @Suppress("DEPRECATION")
    private fun securedNetwork(quotedSsid: String, password: String, security: ConnectSecurity): WifiConfiguration? {
        val config = WifiConfiguration().apply {
            SSID = quotedSsid
            preSharedKey = "\"$password\""
        }
        if (security == ConnectSecurity.SAE) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        }
        return config
    }

    // Android shows Wi-Fi networks only to apps with the location permission, and only while Location is on.
    // As device owner, the launcher grants itself the permission, then leaves it under the user's control.
    private fun locationProblem(context: Context): String? {
        if (!grantOwnPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return "Error: Not authorized to control networking."
        }
        val location = context.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !location.isLocationEnabled) {
            return "Error: Location is off. Android hides Wi-Fi networks while it is off."
        }
        return null
    }

    private fun wifiManager(context: Context) =
        context.applicationContext.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION")
    private fun currentBssid(wifi: WifiManager): String? = wifi.connectionInfo?.bssid?.uppercase()

    @Suppress("DEPRECATION")
    private fun ssidOf(result: ScanResult): String = result.SSID.orEmpty()

    private fun print(line: String) = CommandResult.Output(listOf(OutputLine(line)))

    private fun error(message: String) = CommandResult.Output(listOf(OutputLine(message, isError = true)))
}

// nmcli accepts any prefix of a keyword; the first match in nmcli's own order wins
internal fun matchArgument(word: String, options: List<String>): String? =
    if (word.isEmpty()) null else options.firstOrNull { it.startsWith(word) }

// Signal quality in percent from dBm: -100 dBm or less is 0, -50 dBm or more is 100
internal fun signalPercent(dbm: Int): Int = (2 * (dbm + 100)).coerceIn(0, 100)

// nmcli's bars: one more bar above 5, 30, 55 and 80 percent
internal fun signalBars(percent: Int): String = when {
    percent > 80 -> "▂▄▆█"
    percent > 55 -> "▂▄▆_"
    percent > 30 -> "▂▄__"
    percent > 5 -> "▂___"
    else -> "____"
}

internal fun channelOf(frequency: Int): Int? = when (frequency) {
    2484 -> 14
    in 2412..2472 -> (frequency - 2407) / 5
    in 5160..5885 -> (frequency - 5000) / 5
    in 5955..7115 -> (frequency - 5950) / 5
    else -> null
}

// nmcli's SECURITY column, from Android's capability string such as "[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"
internal fun securityOf(capabilities: String): String {
    val parts = buildList {
        if ("WEP" in capabilities) add("WEP")
        if ("[WPA-" in capabilities) add("WPA1")
        if ("[RSN-" in capabilities || "[WPA2-" in capabilities) {
            if ("PSK" in capabilities || "EAP" in capabilities) add("WPA2")
            if ("SAE" in capabilities) add("WPA3")
        }
        if ("EAP" in capabilities) add("802.1X")
    }
    return parts.joinToString(" ").ifEmpty { "--" }
}

internal enum class ConnectSecurity { OPEN, PSK, SAE, UNSUPPORTED }

// How to join a network: a password network that also accepts WPA2 is joined with WPA2
internal fun connectSecurity(capabilities: String): ConnectSecurity = when {
    "EAP" in capabilities || "WEP" in capabilities -> ConnectSecurity.UNSUPPORTED
    "PSK" in capabilities -> ConnectSecurity.PSK
    "SAE" in capabilities -> ConnectSecurity.SAE
    else -> ConnectSecurity.OPEN
}

// Columns padded to their widest cell and separated by two spaces, like nmcli's tables
internal fun formatTable(header: List<String>, rows: List<List<String>>): List<String> {
    val all = listOf(header) + rows
    val widths = header.indices.map { column -> all.maxOf { it[column].length } }
    return all.map { row ->
        row.mapIndexed { column, cell -> cell.padEnd(widths[column]) }.joinToString("  ").trimEnd()
    }
}
