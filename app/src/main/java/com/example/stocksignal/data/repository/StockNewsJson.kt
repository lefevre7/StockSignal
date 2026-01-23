package com.example.stocksignal.data.repository

import com.example.stocksignal.domain.model.StockNewsItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object StockNewsJson {

    fun toJson(items: List<StockNewsItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val json = JSONObject()
            json.put("title", item.title)
            json.put("publishedAtText", item.publishedAtText)
            item.publishedAt?.let { json.put("publishedAt", it.toString()) }
            item.source?.let { json.put("source", it) }
            item.url?.let { json.put("url", it) }
            item.translatedTitle?.let { json.put("translatedTitle", it) }
            item.translatedPublishedAtText?.let { json.put("translatedPublishedAtText", it) }
            array.put(json)
        }
        return array.toString()
    }

    fun fromJson(raw: String?): List<StockNewsItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            val publishedAtRaw = json.optString("publishedAt").takeIf { it.isNotBlank() }
            val publishedAt = publishedAtRaw?.let { 
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null // Gracefully handle old LocalDateTime format
                }
            }
            StockNewsItem(
                title = json.optString("title"),
                publishedAtText = json.optString("publishedAtText"),
                publishedAt = publishedAt,
                source = json.optString("source").ifBlank { null },
                url = json.optString("url").ifBlank { null },
                translatedTitle = json.optString("translatedTitle").ifBlank { null },
                translatedPublishedAtText = json.optString("translatedPublishedAtText").ifBlank { null }
            )
        }
    }
}
