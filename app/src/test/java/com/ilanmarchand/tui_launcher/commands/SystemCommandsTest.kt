package com.ilanmarchand.tui_launcher.commands

import android.os.BatteryManager
import com.ilanmarchand.tui_launcher.system.Battery
import com.ilanmarchand.tui_launcher.system.fastfetchSize
import com.ilanmarchand.tui_launcher.system.uptimeLine
import com.ilanmarchand.tui_launcher.system.uptimePretty
import com.ilanmarchand.tui_launcher.system.uptimeShort
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

// Expected outputs come from the real tools on the PC (date, uptime, uname, free, df, cal, fastfetch)
class SystemCommandsTest {
    private val now = ZonedDateTime.of(2026, 10, 17, 15, 1, 9, 0, ZoneId.of("Europe/Paris"))

    private fun out(vararg lines: String) = CommandResult.Output(lines.map { OutputLine(it) })

    @Test
    fun dateFormats() {
        assertEquals(out("Sat Oct 17 15:01:09 CEST 2026"), date(emptyList(), now))
        assertEquals(out("Sat Oct 17 13:01:09 UTC 2026"), date(listOf("-u"), now))
        assertEquals(out("2026-10-17"), date(listOf("-I"), now))
        assertEquals(out("Sat, 17 Oct 2026 15:01:09 +0200"), date(listOf("-R"), now))
        assertEquals(out("2026-10-17 15:01 Sat 290 1792242069"), date(listOf("+%Y-%m-%d %H:%M %a %j %s"), now))
        assertEquals(out("03:01 PM +02:00 %Q"), date(listOf("+%I:%M %p %:z %Q"), now))
        assertEquals(
            CommandResult.Output(
                listOf(OutputLine("date: invalid option -- 'Q'", true), OutputLine("Try 'date --help' for more information.", true))
            ),
            date(listOf("-Q"), now)
        )
        assertEquals(
            CommandResult.Output(listOf(OutputLine("date: cannot set date: Operation not permitted", true))),
            date(listOf("2026-01-01"), now)
        )
    }

    @Test
    fun uptimeFormats() {
        val up = 3 * 3600L + 19 * 60
        assertEquals(" 15:01:09 up  3:19,  1 user,  load average: 0.11, 0.11, 0.07", uptimeLine(LocalTime.of(15, 1, 9), up, listOf(0.11, 0.11, 0.07)))
        assertEquals(" 15:01:09 up 2 days, 42 min,  1 user", uptimeLine(LocalTime.of(15, 1, 9), 2 * 86_400L + 42 * 60, null))
        assertEquals("up 3 hours, 19 minutes", uptimePretty(up))
        assertEquals("up 1 week, 1 day, 1 hour, 1 minute", uptimePretty(8 * 86_400L + 3660))
        assertEquals("up 0 minutes", uptimePretty(30))
        assertEquals("3 hours, 19 mins", uptimeShort(up))
        assertEquals(out("2026-10-17 11:42:05"), uptime(listOf("-s"), LocalDateTime.of(2026, 10, 17, 15, 1, 9), up + 4, null))
    }

    @Test
    fun unameFields() {
        val info = UnameInfo("4.14.186-perf+", "#1 SMP PREEMPT Wed Jun 1 2022", "aarch64")
        assertEquals(out("Linux"), uname(emptyList(), info))
        assertEquals(out("Linux phone 4.14.186-perf+ #1 SMP PREEMPT Wed Jun 1 2022 aarch64 Android"), uname(listOf("-a"), info))
        assertEquals(out("Linux 4.14.186-perf+ aarch64"), uname(listOf("-srm"), info))
        assertEquals(out("Android"), uname(listOf("-o"), info))
    }

    private val meminfo = mapOf(
        "MemTotal" to 16277332L, "MemFree" to 557432L, "Buffers" to 0L, "Cached" to 9282968L, "SReclaimable" to 0L,
        "Shmem" to 108600L, "MemAvailable" to 8854008L, "SwapTotal" to 32554788L, "SwapFree" to 32554788L
    )

    @Test
    fun freeTables() {
        assertEquals(
            out(
                "               total        used        free      shared  buff/cache   available",
                "Mem:        16277332     7423324      557432      108600     9282968     8854008",
                "Swap:       32554788           0    32554788"
            ),
            free(emptyList(), meminfo)
        )
        assertEquals(
            out(
                "               total        used        free      shared  buff/cache   available",
                "Mem:            15Gi       7.1Gi       544Mi       106Mi       8.9Gi       8.4Gi",
                "Swap:           31Gi          0B        31Gi"
            ),
            free(listOf("-h"), meminfo)
        )
        assertEquals(
            "Mem:           15895        7249         544         106        9065        8646",
            (free(listOf("-m"), meminfo) as CommandResult.Output).lines[1].text
        )
    }

    @Test
    fun dfTables() {
        val root = DfRow("/dev/mapper/root", 232314880L * 1024, 212286400L * 1024, 19399312L * 1024, "/")
        assertEquals(
            listOf("Filesystem        Size  Used Avail Use% Mounted on", "/dev/mapper/root  222G  203G   19G  92% /"),
            dfTable(listOf(root), human = true)
        )
        assertEquals(
            listOf(
                "Filesystem       1K-blocks      Used Available Use% Mounted on",
                "/dev/mapper/root 232314880 212286400  19399312  92% /"
            ),
            dfTable(listOf(root), human = false)
        )
    }

    @Test
    fun calendars() {
        assertEquals(
            listOf(
                "    October 2026    ",
                "Su Mo Tu We Th Fr Sa",
                "             1  2  3",
                " 4  5  6  7  8  9 10",
                "11 12 13 14 15 16 17",
                "18 19 20 21 22 23 24",
                "25 26 27 28 29 30 31",
                "                    "
            ),
            calendarMonth(10, 2026, mondayFirst = false)
        )
        assertEquals("    February 2024   ", calendarMonth(2, 2024, mondayFirst = false)[0])
        assertEquals("25 26 27 28 29      ", calendarMonth(2, 2024, mondayFirst = false)[6])
        assertEquals(listOf("Mo Tu We Th Fr Sa Su", "          1  2  3  4"), calendarMonth(10, 2026, mondayFirst = true).subList(1, 3))
        val today = LocalDate.of(2026, 10, 17)
        assertEquals(out(*calendarMonth(2, 2024, false).toTypedArray()), cal(listOf("feb", "2024"), today))
        assertEquals(12 * 8, (cal(listOf("2026"), today) as CommandResult.Output).lines.size)
        assertEquals(CommandResult.Output(listOf(OutputLine("cal: illegal month value: use 1-12", true))), cal(listOf("13", "2026"), today))
    }

    @Test
    fun batteryLines() {
        assertEquals("Battery 0: Discharging, 85%, rate information unavailable", acpiBatteryLine(85, BatteryManager.BATTERY_STATUS_DISCHARGING, -1))
        assertEquals("Battery 0: Charging, 50%, 01:07:00 until charged", acpiBatteryLine(50, BatteryManager.BATTERY_STATUS_CHARGING, 4_020_000))
        assertEquals("Battery 0: Full, 100%", acpiBatteryLine(100, BatteryManager.BATTERY_STATUS_FULL, -1))
        val battery = Battery(85, BatteryManager.BATTERY_STATUS_DISCHARGING, 0, -1)
        assertEquals(out("Battery 0: Discharging, 85%, rate information unavailable", "Adapter 0: off-line"), acpi(listOf("-V"), battery))
    }

    @Test
    fun fastfetchLayout() {
        assertEquals("0 B", fastfetchSize(0))
        assertEquals("7.06 GiB", fastfetchSize(7_580_000_000))
        val lines = fastfetchLines("Boudbois@phone", listOf("OS" to "Android 11 aarch64"))
        assertEquals(OutputLine("Boudbois@phone", accentLength = 14), lines[7])
        assertEquals(OutputLine("--------------"), lines[8])
        assertEquals(OutputLine("OS: Android 11 aarch64", accentLength = 2), lines[9])
    }
}
