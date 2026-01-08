package com.example.stocksignal.domain.model

import org.json.JSONArray
import org.json.JSONObject

object IndicatorAlertJson {

    fun toJson(alerts: List<IndicatorAlertSetting>): String? {
        if (alerts.isEmpty()) return null
        val array = JSONArray()
        alerts.forEach { alert ->
            val json = JSONObject()
            json.put("metric", alert.metric.name)
            json.put("threshold", alert.threshold)
            json.put("direction", alert.direction.name)
            json.put("enabled", alert.enabled)
            array.put(json)
        }
        return array.toString()
    }

    fun fromJson(value: String?): List<IndicatorAlertSetting> {
        if (value.isNullOrBlank()) return emptyList()
        val array = JSONArray(value)
        val results = mutableListOf<IndicatorAlertSetting>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val metric = runCatching {
                IndicatorMetric.valueOf(json.optString("metric"))
            }.getOrNull() ?: continue
            val direction = runCatching {
                AlertDirection.valueOf(json.optString("direction"))
            }.getOrNull() ?: metric.defaultDirection
            val threshold = json.optDouble("threshold", metric.defaultThreshold)
            val enabled = json.optBoolean("enabled", false)
            results.add(
                IndicatorAlertSetting(
                    metric = metric,
                    threshold = threshold,
                    direction = direction,
                    enabled = enabled
                )
            )
        }
        return results
    }
}
