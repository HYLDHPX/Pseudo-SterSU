package me.weishu.kernelsu.ui.component.bottombar

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarTimeTextTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun timestamp(hour: Int): Long = Calendar.getInstance(utc, Locale.US).apply {
        set(2026, Calendar.OCTOBER, 12, hour, 5, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun twelveHourClockDistinguishesMorningAndAfternoon() {
        assertEquals(SidebarTimeText("9:05", "AM"), sidebarTimeText(timestamp(9), Locale.US, false, utc))
        assertEquals(SidebarTimeText("9:05", "PM"), sidebarTimeText(timestamp(21), Locale.US, false, utc))
    }

    @Test
    fun twentyFourHourClockDoesNotShowPeriod() {
        assertEquals(SidebarTimeText("09:05", null), sidebarTimeText(timestamp(9), Locale.US, true, utc))
        assertEquals(SidebarTimeText("21:05", null), sidebarTimeText(timestamp(21), Locale.US, true, utc))
    }

    @Test
    fun chineseTwelveHourClockUsesLocalizedPeriod() {
        assertEquals("上午", sidebarTimeText(timestamp(9), Locale.SIMPLIFIED_CHINESE, false, utc).period)
        assertEquals("下午", sidebarTimeText(timestamp(21), Locale.SIMPLIFIED_CHINESE, false, utc).period)
    }
}
