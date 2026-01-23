package com.example.stocksignal.data.local.db

import androidx.room.TypeConverter
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.domain.model.PriceCandle
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? {
        return value?.format(dateFormatter)
    }

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it, dateFormatter) }
    }

    @TypeConverter
    fun localDateTimeToString(value: LocalDateTime?): String? {
        return value?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun stringToLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, dateTimeFormatter) }
    }

    @TypeConverter
    fun instantToString(value: Instant?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun stringToInstant(value: String?): Instant? {
        return value?.let { Instant.parse(it) }
    }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? {
        return value?.let { JSONArray(it).toString() }
    }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val array = JSONArray(value)
        return List(array.length()) { index -> array.optString(index) }
    }

    @TypeConverter
    fun mapToJson(value: Map<String, Int>?): String? {
        if (value == null) return null
        val json = JSONObject()
        value.forEach { (key, mapValue) ->
            json.put(key, mapValue)
        }
        return json.toString()
    }

    @TypeConverter
    fun jsonToMap(value: String?): Map<String, Int> {
        if (value.isNullOrBlank()) return emptyMap()
        val json = JSONObject(value)
        val keys = json.keys()
        val result = mutableMapOf<String, Int>()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.optInt(key)
        }
        return result
    }

    @TypeConverter
    fun marketMoversToJson(value: List<MarketMoverItem>?): String? {
        if (value == null) return null
        val array = JSONArray()
        value.forEach { item ->
            val json = JSONObject()
            json.put("ticker", item.ticker)
            json.put("companyName", item.companyName)
            json.put("exchange", item.exchange)
            json.put("price", item.price)
            json.put("percentChange", item.percentChange)
            json.put("rank", item.rank)
            json.put("signalScore", item.signalScore)
            json.put("signalLabel", item.signalLabel)
            
            // Serialize series (PriceCandle list)
            if (item.series.isNotEmpty()) {
                val seriesArray = JSONArray()
                item.series.forEach { candle ->
                    val candleJson = JSONObject()
                    candleJson.put("time", candle.time.format(dateTimeFormatter))
                    candleJson.put("open", candle.open)
                    candleJson.put("high", candle.high)
                    candleJson.put("low", candle.low)
                    candleJson.put("close", candle.close)
                    candleJson.put("volume", candle.volume)
                    seriesArray.put(candleJson)
                }
                json.put("series", seriesArray)
            }
            
            array.put(json)
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToMarketMovers(value: String?): List<MarketMoverItem> {
        if (value.isNullOrBlank()) return emptyList()
        val array = JSONArray(value)
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val price = if (json.has("price")) json.optDouble("price") else null
            val percentChange = if (json.has("percentChange")) json.optDouble("percentChange") else null
            val rank = if (json.has("rank")) json.optInt("rank") else null
            val signalScore = if (json.has("signalScore")) json.optInt("signalScore") else null
            
            // Deserialize series (PriceCandle list)
            val series = if (json.has("series")) {
                val seriesArray = json.getJSONArray("series")
                List(seriesArray.length()) { seriesIndex ->
                    val candleJson = seriesArray.getJSONObject(seriesIndex)
                    PriceCandle(
                        time = LocalDateTime.parse(candleJson.getString("time"), dateTimeFormatter),
                        open = candleJson.getDouble("open"),
                        high = candleJson.getDouble("high"),
                        low = candleJson.getDouble("low"),
                        close = candleJson.getDouble("close"),
                        volume = candleJson.getLong("volume")
                    )
                }
            } else {
                emptyList()
            }
            
            MarketMoverItem(
                ticker = json.optString("ticker"),
                companyName = json.optString("companyName"),
                exchange = json.optString("exchange").ifBlank { null },
                price = price,
                percentChange = percentChange,
                rank = rank,
                signalScore = signalScore,
                signalLabel = json.optString("signalLabel").ifBlank { null },
                series = series
            )
        }
    }
}
