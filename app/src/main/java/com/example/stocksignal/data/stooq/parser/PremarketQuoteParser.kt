package com.example.stocksignal.data.stooq.parser

import android.util.Log
import com.example.stocksignal.data.stooq.model.PremarketQuote
import org.jsoup.Jsoup

object PremarketQuoteParser {

    private const val TAG = "PremarketQuoteParser"
    private val numberRegex = Regex("([+-]?\\d+(?:[\\.,]\\d+)?)")
    private val volumeRegex = Regex("([+-]?\\d+(?:[\\.,]\\d+)?)([mMgG]?)")

    fun parse(html: String, ticker: String): PremarketQuote? {
        if (html.isBlank()) return null

        return try {
            val doc = Jsoup.parse(html)
            val bid = findNumericValue(doc, "Bid")
            val ask = findNumericValue(doc, "Ask")
            val volume = findVolumeValue(doc, "Volume")
            if (bid == null && ask == null && volume == null) {
                null
            } else {
                PremarketQuote(
                    ticker = ticker,
                    bid = bid,
                    ask = ask,
                    volume = volume
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse quote HTML for $ticker", e)
            null
        }
    }

    private fun findNumericValue(doc: org.jsoup.nodes.Document, label: String): Double? {
        val elements = doc.select("*:matchesOwn((?i)\\b${Regex.escape(label)}\\b)")
        for (element in elements) {
            val cell = element.closest("td") ?: element.parent() ?: continue
            val spans = cell.select("span")
            for (span in spans) {
                val value = parseNumber(span.text())
                if (value != null) return value
            }
            val fallback = parseNumber(cell.text())
            if (fallback != null) return fallback
        }
        return null
    }

    private fun findVolumeValue(doc: org.jsoup.nodes.Document, label: String): Long? {
        val elements = doc.select("*:matchesOwn((?i)\\b${Regex.escape(label)}\\b)")
        for (element in elements) {
            val cell = element.closest("td") ?: element.parent() ?: continue
            val spans = cell.select("span")
            for (span in spans) {
                val value = parseVolume(span.text())
                if (value != null) return value
            }
            val fallback = parseVolume(cell.text())
            if (fallback != null) return fallback
        }
        return null
    }

    private fun parseNumber(text: String): Double? {
        val normalized = text.trim().replace(",", ".")
        val match = numberRegex.find(normalized) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    private fun parseVolume(text: String): Long? {
        val normalized = text.trim().replace(",", ".")
        val match = volumeRegex.find(normalized) ?: return null
        val number = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val suffix = match.groupValues.getOrNull(2)?.lowercase()
        val multiplier = when (suffix) {
            "m" -> 1_000_000.0
            "g" -> 1_000_000_000.0
            else -> 1.0
        }
        return (number * multiplier).toLong()
    }
}
