package com.example.stocksignal.data.settings

import org.json.JSONArray
import org.json.JSONObject

object SettingsJson {

    fun encodeScheduleWindows(windows: List<ScheduleWindow>): String {
        val array = JSONArray()
        windows.forEach { window ->
            val json = JSONObject()
            json.put("id", window.id)
            json.put("type", window.type.name)
            json.put("hour", window.hour)
            json.put("minute", window.minute)
            json.put("zoneId", window.zoneId)
            json.put("offsetMinutes", window.offsetMinutes)
            array.put(json)
        }
        return array.toString()
    }

    fun decodeScheduleWindows(raw: String?): List<ScheduleWindow> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val typeName = json.optString("type")
            val type = runCatching { ScheduleWindowType.valueOf(typeName) }
                .getOrDefault(ScheduleWindowType.FIXED_LOCAL)
            ScheduleWindow(
                id = json.optString("id").ifBlank { "window_$index" },
                type = type,
                hour = if (json.has("hour")) json.optInt("hour") else null,
                minute = if (json.has("minute")) json.optInt("minute") else null,
                zoneId = json.optString("zoneId").ifBlank { null },
                offsetMinutes = if (json.has("offsetMinutes")) json.optInt("offsetMinutes") else null
            )
        }
    }
}
