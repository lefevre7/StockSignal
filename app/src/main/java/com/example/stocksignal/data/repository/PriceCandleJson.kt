package com.example.stocksignal.data.repository

import com.example.stocksignal.domain.model.PriceCandle
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PriceCandleJson {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun toJson(candles: List<PriceCandle>): String {
        val array = JSONArray()
        candles.forEach { candle ->
            val json = JSONObject()
            json.put("time", candle.time.format(formatter))
            json.put("open", candle.open)
            json.put("high", candle.high)
            json.put("low", candle.low)
            json.put("close", candle.close)
            json.put("volume", candle.volume)
            array.put(json)
        }
        return array.toString()
    }

    fun fromJson(raw: String?): List<PriceCandle> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            PriceCandle(
                time = LocalDateTime.parse(json.getString("time"), formatter),
                open = json.getDouble("open"),
                high = json.getDouble("high"),
                low = json.getDouble("low"),
                close = json.getDouble("close"),
                volume = json.optLong("volume")
            )
        }
    }
}
