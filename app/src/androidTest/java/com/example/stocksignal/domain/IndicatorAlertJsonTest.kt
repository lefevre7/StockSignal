package com.example.stocksignal.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for IndicatorAlertJson serialization/deserialization.
 * These tests require Android framework (JSONObject) so must run on device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class IndicatorAlertJsonTest {

    @Test
    fun alertSettingsSerializeAndDeserializeCorrectly() {
        val originalAlerts = listOf(
            IndicatorAlertSetting(
                metric = IndicatorMetric.RSI_14,
                threshold = 30.0,
                direction = AlertDirection.BELOW,
                enabled = true
            ),
            IndicatorAlertSetting(
                metric = IndicatorMetric.MACD_HISTOGRAM,
                threshold = 0.0,
                direction = AlertDirection.ABOVE,
                enabled = true
            ),
            IndicatorAlertSetting(
                metric = IndicatorMetric.SMA_50_DISTANCE,
                threshold = -5.0,
                direction = AlertDirection.BELOW,
                enabled = false
            )
        )

        val json = IndicatorAlertJson.toJson(originalAlerts)
        val deserializedAlerts = IndicatorAlertJson.fromJson(json)

        assertEquals("Should have same number of alerts", originalAlerts.size, deserializedAlerts.size)
        
        originalAlerts.forEachIndexed { index, original ->
            val deserialized = deserializedAlerts[index]
            assertEquals("Metric should match", original.metric, deserialized.metric)
            assertEquals("Threshold should match", original.threshold, deserialized.threshold, 0.001)
            assertEquals("Direction should match", original.direction, deserialized.direction)
            assertEquals("Enabled should match", original.enabled, deserialized.enabled)
        }
    }

    @Test
    fun nullJsonReturnsEmptyList() {
        val result = IndicatorAlertJson.fromJson(null)
        assertTrue("Null JSON should return empty list", result.isEmpty())
    }

    @Test
    fun emptyJsonReturnsEmptyList() {
        val result = IndicatorAlertJson.fromJson("")
        assertTrue("Empty JSON should return empty list", result.isEmpty())
        
        val blankResult = IndicatorAlertJson.fromJson("   ")
        assertTrue("Blank JSON should return empty list", blankResult.isEmpty())
    }

    @Test
    fun invalidJsonThrowsException() {
        // The current implementation doesn't catch JSON parsing exceptions
        // so invalid JSON will throw JSONException
        assertThrows(org.json.JSONException::class.java) {
            IndicatorAlertJson.fromJson("not valid json")
        }
        
        assertThrows(org.json.JSONException::class.java) {
            IndicatorAlertJson.fromJson("{invalid: json}")
        }
    }

    @Test
    fun emptyArrayReturnsEmptyList() {
        val json = JSONArray().toString()
        val result = IndicatorAlertJson.fromJson(json)
        assertTrue("Empty array should return empty list", result.isEmpty())
    }

    @Test
    fun missingFieldsUsesDefaults() {
        val jsonArray = JSONArray()
        val alertObject = JSONObject().apply {
            put("metric", "RSI_14")
            // Missing threshold, direction, enabled
        }
        jsonArray.put(alertObject)

        val result = IndicatorAlertJson.fromJson(jsonArray.toString())
        
        assertEquals("Should have one alert", 1, result.size)
        val alert = result[0]
        assertEquals("Metric should be RSI_14", IndicatorMetric.RSI_14, alert.metric)
        assertEquals("Should use default threshold", 30.0, alert.threshold, 0.001)
        assertEquals("Should use default direction", AlertDirection.BELOW, alert.direction)
        assertEquals("Should be disabled by default", false, alert.enabled)
    }

    @Test
    fun unknownMetricIsSkipped() {
        val jsonArray = JSONArray()
        jsonArray.put(JSONObject().apply {
            put("metric", "UNKNOWN_METRIC")
            put("threshold", 50.0)
            put("direction", "ABOVE")
            put("enabled", true)
        })
        jsonArray.put(JSONObject().apply {
            put("metric", "RSI_14")
            put("threshold", 30.0)
            put("direction", "BELOW")
            put("enabled", true)
        })

        val result = IndicatorAlertJson.fromJson(jsonArray.toString())
        
        assertEquals("Should skip unknown metric", 1, result.size)
        assertEquals("Valid metric should be preserved", IndicatorMetric.RSI_14, result[0].metric)
    }

    @Test
    fun unknownDirectionUsesDefault() {
        val jsonArray = JSONArray()
        jsonArray.put(JSONObject().apply {
            put("metric", "RSI_14")
            put("threshold", 30.0)
            put("direction", "SIDEWAYS") // Invalid direction
            put("enabled", true)
        })

        val result = IndicatorAlertJson.fromJson(jsonArray.toString())
        
        assertEquals("Should have one alert", 1, result.size)
        assertEquals("Should use default direction", AlertDirection.BELOW, result[0].direction)
    }

    @Test
    fun serializeEmptyListReturnsNull() {
        val json = IndicatorAlertJson.toJson(emptyList())
        assertNull("Empty list should serialize to null", json)
    }

    @Test
    fun roundTripPreservesAllData() {
        val allMetrics = IndicatorMetric.entries.map { metric ->
            IndicatorAlertSetting(
                metric = metric,
                threshold = metric.defaultThreshold,
                direction = if (metric.defaultThreshold > 0) AlertDirection.ABOVE else AlertDirection.BELOW,
                enabled = true
            )
        }

        val json = IndicatorAlertJson.toJson(allMetrics)
        val result = IndicatorAlertJson.fromJson(json)

        assertEquals("Should preserve all metrics", allMetrics.size, result.size)
        allMetrics.forEachIndexed { index, original ->
            val deserialized = result[index]
            assertEquals("Metric ${original.metric} should match", original.metric, deserialized.metric)
            assertEquals("Threshold for ${original.metric} should match", original.threshold, deserialized.threshold, 0.001)
            assertEquals("Direction for ${original.metric} should match", original.direction, deserialized.direction)
            assertEquals("Enabled for ${original.metric} should match", original.enabled, deserialized.enabled)
        }
    }

    @Test
    fun disabledAlertsArePreserved() {
        val alerts = listOf(
            IndicatorAlertSetting(IndicatorMetric.RSI_14, 30.0, AlertDirection.BELOW, enabled = false),
            IndicatorAlertSetting(IndicatorMetric.MACD_HISTOGRAM, 0.0, AlertDirection.ABOVE, enabled = true)
        )

        val json = IndicatorAlertJson.toJson(alerts)
        val result = IndicatorAlertJson.fromJson(json)

        assertEquals("First alert should be disabled", false, result[0].enabled)
        assertEquals("Second alert should be enabled", true, result[1].enabled)
    }

    @Test
    fun negativeThresholdsArePreserved() {
        val alert = IndicatorAlertSetting(
            metric = IndicatorMetric.SMA_50_DISTANCE,
            threshold = -10.5,
            direction = AlertDirection.BELOW,
            enabled = true
        )

        val json = IndicatorAlertJson.toJson(listOf(alert))
        val result = IndicatorAlertJson.fromJson(json)

        assertEquals("Negative threshold should be preserved", -10.5, result[0].threshold, 0.001)
    }

    @Test
    fun zeroThresholdIsPreserved() {
        val alert = IndicatorAlertSetting(
            metric = IndicatorMetric.MACD_HISTOGRAM,
            threshold = 0.0,
            direction = AlertDirection.ABOVE,
            enabled = true
        )

        val json = IndicatorAlertJson.toJson(listOf(alert))
        val result = IndicatorAlertJson.fromJson(json)

        assertEquals("Zero threshold should be preserved", 0.0, result[0].threshold, 0.001)
    }

    @Test
    fun largeNumbersAreHandledCorrectly() {
        val alert = IndicatorAlertSetting(
            metric = IndicatorMetric.RSI_14,
            threshold = 999999.99,
            direction = AlertDirection.ABOVE,
            enabled = true
        )

        val json = IndicatorAlertJson.toJson(listOf(alert))
        val result = IndicatorAlertJson.fromJson(json)

        assertEquals("Large threshold should be preserved", 999999.99, result[0].threshold, 0.01)
    }

    @Test
    fun verySmallNumbersAreHandledCorrectly() {
        val alert = IndicatorAlertSetting(
            metric = IndicatorMetric.ATR_PERCENT,
            threshold = 0.001,
            direction = AlertDirection.ABOVE,
            enabled = true
        )

        val json = IndicatorAlertJson.toJson(listOf(alert))
        val result = IndicatorAlertJson.fromJson(json)

        assertEquals("Very small threshold should be preserved", 0.001, result[0].threshold, 0.0001)
    }
}
