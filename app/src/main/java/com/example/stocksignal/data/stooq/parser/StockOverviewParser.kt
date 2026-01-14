package com.example.stocksignal.data.stooq.parser

import android.util.Log
import com.example.stocksignal.domain.model.StockOverview
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parser for extracting stock overview/fundamental data from Stooq quote page HTML.
 * 
 * Looks for nested tables containing:
 * - "Max/min 52t" → 52-week high/low
 * - "Stopa dywidendy" → Dividend
 * - "Kapitalizacja" → Market Cap
 * - "C/Z" → P/E Ratio
 */
object StockOverviewParser {

    private const val TAG = "StockOverviewParser"

    // Regex to match numbers with optional thousands separators, decimals, and multipliers (M, B, T)
    private val numberRegex = Regex("([+-]?[\\d,]+(?:\\.\\d+)?)\\s*([MBTmbt])?")

    fun parse(html: String, symbol: String): StockOverview {
        if (html.isBlank()) {
            logW("Empty HTML for $symbol")
            return StockOverview(symbol = symbol)
        }

        try {
            val doc = Jsoup.parse(html)
            
            // Find all tables to search for our data
            val tables = doc.select("table")
            
            var marketCap: Double? = null
            var peRatio: Double? = null
            var dividend: Double? = null
            var week52High: Double? = null
            var week52Low: Double? = null

            tables.forEach { table ->
                table.select("tr").forEach { row ->
                    val cells = row.select("td")
                    if (cells.size >= 2) {
                        val label = cells[0].text().trim()
                        val value = cells[1].text().trim()

                        when {
                            // Market Cap - "Kapitalizacja"
                            label.contains("Kapitalizacja", ignoreCase = true) -> {
                                marketCap = parseNumber(value)
                                if (marketCap != null) {
                                    logD("$symbol: Found Market Cap = $marketCap")
                                }
                            }
                            // P/E Ratio - "C/Z"
                            label.equals("C/Z", ignoreCase = true) || 
                            label.contains("C/Z", ignoreCase = true) -> {
                                peRatio = parseNumber(value)
                                if (peRatio != null) {
                                    logD("$symbol: Found P/E = $peRatio")
                                }
                            }
                            // Dividend - "Stopa dywidendy"
                            label.contains("Stopa dywidendy", ignoreCase = true) -> {
                                dividend = parseNumber(value)
                                if (dividend != null) {
                                    logD("$symbol: Found Dividend = $dividend")
                                }
                            }
                            // 52-week high/low - "Max/min 52t"
                            label.contains("Max/min 52t", ignoreCase = true) -> {
                                val parts = value.split("/")
                                if (parts.size == 2) {
                                    week52High = parseNumber(parts[0].trim())
                                    week52Low = parseNumber(parts[1].trim())
                                    logD("$symbol: Found 52W High/Low = $week52High / $week52Low")
                                }
                            }
                        }
                    }
                }
            }

            return StockOverview(
                symbol = symbol,
                marketCap = marketCap,
                peRatio = peRatio,
                dividend = dividend,
                week52High = week52High,
                week52Low = week52Low
            )
        } catch (e: Exception) {
            logE("Error parsing overview for $symbol", e)
            return StockOverview(symbol = symbol)
        }
    }

    /**
     * Parse a number string that may include:
     * - Thousands separators (commas)
     * - Decimal points
     * - Multiplier suffixes (M, B, T for million, billion, trillion)
     * 
     * Examples:
     * - "123.45" → 123.45
     * - "1,234.56" → 1234.56
     * - "1.23M" → 1230000.0
     * - "45.6B" → 45600000000.0
     * - "N/A" → null
     */
    private fun parseNumber(text: String): Double? {
        if (text.isBlank() || text.contains("N/A", ignoreCase = true) || text == "-") {
            return null
        }

        val match = numberRegex.find(text) ?: return null
        val numberStr = match.groupValues.getOrNull(1)?.replace(",", "")?.trim() ?: return null
        val multiplier = match.groupValues.getOrNull(2)?.uppercase()?.trim()

        val baseValue = numberStr.toDoubleOrNull() ?: return null

        return when (multiplier) {
            "M" -> baseValue * 1_000_000
            "B" -> baseValue * 1_000_000_000
            "T" -> baseValue * 1_000_000_000_000
            else -> baseValue
        }
    }

    private fun logD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logW(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun logE(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
