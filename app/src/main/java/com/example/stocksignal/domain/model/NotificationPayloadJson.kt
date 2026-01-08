package com.example.stocksignal.domain.model

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object NotificationPayloadJson {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun toJson(payload: NotificationPayload): String {
        val json = JSONObject()
        json.put("type", payload.type.name.lowercase())
        json.put("ticker", payload.ticker)
        json.put("company", payload.company)
        json.put("signal", payload.signal)
        json.put("score", payload.score)
        json.put("confidence", payload.confidence)
        json.put("price", payload.price)
        json.put("percentChange", payload.percentChange)
        json.put("time", payload.time.format(formatter))
        json.put("deep_link", payload.deepLink)
        json.put("source", payload.source)
        return json.toString()
    }

    fun fromJson(raw: String): NotificationPayload? {
        return try {
            val json = JSONObject(raw)
            val type = json.optString("type").uppercase()
            val timeRaw = json.optString("time")
            NotificationPayload(
                type = NotificationEventType.valueOf(type),
                ticker = json.optString("ticker"),
                company = json.optString("company").ifBlank { null },
                signal = json.optString("signal"),
                score = json.optInt("score"),
                confidence = json.optInt("confidence"),
                price = json.optDouble("price").takeUnless { it.isNaN() },
                percentChange = json.optDouble("percentChange").takeUnless { it.isNaN() },
                time = LocalDateTime.parse(timeRaw, formatter),
                deepLink = json.optString("deep_link").ifBlank { null },
                source = json.optString("source")
            )
        } catch (_: Exception) {
            null
        }
    }
}
