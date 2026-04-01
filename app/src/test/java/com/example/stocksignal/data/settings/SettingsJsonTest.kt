package com.example.stocksignal.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsJsonTest {

    // ---- encodeScheduleWindows ----

    @Test
    fun `encodeScheduleWindows produces valid JSON for empty list`() {
        val result = SettingsJson.encodeScheduleWindows(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun `roundtrip preserves all fields including nulls`() {
        val windows = listOf(
            ScheduleWindow(
                id = "market_open_minus_10",
                type = ScheduleWindowType.MARKET_OPEN_MINUS,
                hour = null,
                minute = null,
                zoneId = "America/New_York",
                offsetMinutes = -10
            ),
            ScheduleWindow(
                id = "local_1100",
                type = ScheduleWindowType.FIXED_LOCAL,
                hour = 11,
                minute = 0,
                zoneId = null,
                offsetMinutes = null
            )
        )
        val encoded = SettingsJson.encodeScheduleWindows(windows)
        val decoded = SettingsJson.decodeScheduleWindows(encoded)
        assertEquals(windows, decoded)
    }

    @Test
    fun `roundtrip preserves all optional fields when all present`() {
        val window = ScheduleWindow(
            id = "full_window",
            type = ScheduleWindowType.FIXED_LOCAL,
            hour = 9,
            minute = 30,
            zoneId = "America/Chicago",
            offsetMinutes = 5
        )
        val encoded = SettingsJson.encodeScheduleWindows(listOf(window))
        val decoded = SettingsJson.decodeScheduleWindows(encoded)
        assertEquals(1, decoded.size)
        assertEquals(window, decoded[0])
    }

    // ---- decodeScheduleWindows ----

    @Test
    fun `decodeScheduleWindows returns empty list for null input`() {
        assertEquals(emptyList<ScheduleWindow>(), SettingsJson.decodeScheduleWindows(null))
    }

    @Test
    fun `decodeScheduleWindows returns empty list for blank string`() {
        assertEquals(emptyList<ScheduleWindow>(), SettingsJson.decodeScheduleWindows(""))
        assertEquals(emptyList<ScheduleWindow>(), SettingsJson.decodeScheduleWindows("   "))
    }

    @Test
    fun `decodeScheduleWindows falls back to FIXED_LOCAL for unknown type`() {
        val raw = """[{"id":"w1","type":"UNKNOWN_TYPE","hour":10,"minute":0}]"""
        val windows = SettingsJson.decodeScheduleWindows(raw)
        assertEquals(1, windows.size)
        assertEquals(ScheduleWindowType.FIXED_LOCAL, windows[0].type)
    }

    @Test
    fun `decodeScheduleWindows uses window_index as id when id is blank`() {
        val raw = """[{"id":"","type":"FIXED_LOCAL","hour":10,"minute":0}]"""
        val windows = SettingsJson.decodeScheduleWindows(raw)
        assertEquals(1, windows.size)
        assertEquals("window_0", windows[0].id)
    }

    @Test
    fun `decodeScheduleWindows uses index-based id when id key is absent`() {
        val raw = """[{"type":"FIXED_LOCAL","hour":10,"minute":0}]"""
        val windows = SettingsJson.decodeScheduleWindows(raw)
        assertEquals(1, windows.size)
        assertEquals("window_0", windows[0].id)
    }

    @Test
    fun `decodeScheduleWindows returns null for optional fields when not present`() {
        val raw = """[{"id":"w0","type":"FIXED_LOCAL"}]"""
        val windows = SettingsJson.decodeScheduleWindows(raw)
        assertEquals(1, windows.size)
        assertNull(windows[0].hour)
        assertNull(windows[0].minute)
        assertNull(windows[0].zoneId)
        assertNull(windows[0].offsetMinutes)
    }

    @Test
    fun `decodeScheduleWindows returns null for zoneId when value is blank`() {
        val raw = """[{"id":"w0","type":"FIXED_LOCAL","zoneId":""}]"""
        val windows = SettingsJson.decodeScheduleWindows(raw)
        assertNull(windows[0].zoneId)
    }

    @Test
    fun `decodeScheduleWindows decodes multiple windows correctly`() {
        val windows = listOf(
            ScheduleWindow("w0", ScheduleWindowType.MARKET_OPEN_MINUS, null, null, "America/New_York", -10),
            ScheduleWindow("w1", ScheduleWindowType.FIXED_LOCAL, 14, 0, null, null)
        )
        val roundtripped = SettingsJson.decodeScheduleWindows(
            SettingsJson.encodeScheduleWindows(windows)
        )
        assertEquals(windows, roundtripped)
    }
}
