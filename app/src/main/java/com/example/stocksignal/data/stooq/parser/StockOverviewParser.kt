package com.example.stocksignal.data.stooq.parser

import android.util.Log
import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.domain.model.StockNewsItem
import com.example.stocksignal.domain.model.StockOverview
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

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
    private const val NEWS_HEADER = "Wiadomości"
    private val polandTimeZone = ZoneId.of("Europe/Warsaw")
    private val displayTimeZone = ZoneId.systemDefault()

    // Regex to match numbers with optional thousands separators, decimals, and multipliers (M, B, T)
    private val numberRegex = Regex("([+-]?[\\d,]+(?:\\.\\d+)?)\\s*([MBTmbt])?")
    private val newsMonthMap = mapOf(
        "sty" to 1,
        "lut" to 2,
        "mar" to 3,
        "kwi" to 4,
        "maj" to 5,
        "cze" to 6,
        "lip" to 7,
        "sie" to 8,
        "wrz" to 9,
        "paź" to 10,
        "paz" to 10,
        "lis" to 11,
        "gru" to 12
    )

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
                week52Low = week52Low,
                news = parseNews(doc)
            )
        } catch (e: Exception) {
            logE("Error parsing overview for $symbol", e)
            return StockOverview(symbol = symbol)
        }
    }

    private fun parseNews(doc: org.jsoup.nodes.Document): List<StockNewsItem> {
        val headerCell = doc.select("td")
            .firstOrNull { it.text().trim().equals(NEWS_HEADER, ignoreCase = true) }
            ?: return emptyList()
        val headerTable = headerCell.closest("table") ?: return emptyList()
        val parent = headerTable.parent() ?: return emptyList()
        val siblings = parent.children()
        val headerIndex = siblings.indexOf(headerTable)
        if (headerIndex == -1) return emptyList()
        val newsTable = siblings.subList(headerIndex + 1, siblings.size)
            .firstOrNull { it.tagName() == "table" } ?: return emptyList()

        val items = mutableListOf<StockNewsItem>()
        newsTable.select("tr").forEach { row ->
            val cells = row.select("td")
            if (cells.size < 2) return@forEach
            val contentCell = cells[1]
            val link = contentCell.selectFirst("a[href]") ?: return@forEach
            val title = extractTitle(link)
            if (title.isBlank()) return@forEach
            val metaText = contentCell.selectFirst("font#a")?.text()?.trim()
                ?: contentCell.select("font").lastOrNull()?.text()?.trim().orEmpty()
            val (dateText, source) = splitNewsMeta(metaText)
            val publishedAt = parseNewsDate(dateText)
            val url = resolveNewsUrl(link.attr("href"))
            items.add(
                StockNewsItem(
                    title = title,
                    publishedAtText = dateText,
                    publishedAt = publishedAt,
                    source = source,
                    url = url
                )
            )
        }
        return items
    }

    private fun extractTitle(link: Element): String {
        val clone = link.clone()
        clone.select("b").remove()
        return clone.text().trim()
    }

    private fun splitNewsMeta(metaText: String): Pair<String, String?> {
        if (metaText.isBlank()) return "" to null
        val parts = metaText.split(" - ", limit = 2)
        val dateText = parts.getOrNull(0)?.trim().orEmpty()
        val source = parts.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() }
        return dateText to source
    }

    private fun parseNewsDate(dateText: String): Instant? {
        if (dateText.isBlank()) return null
        val parts = dateText.split(",")
        if (parts.size < 2) return null
        val datePart = parts[0].trim().lowercase(Locale.ROOT)
        val timePart = parts[1].trim()

        val datePieces = datePart.split(" ").filter { it.isNotBlank() }
        if (datePieces.size < 2) return null
        val day = datePieces[0].toIntOrNull() ?: return null
        val monthKey = datePieces[1].removeSuffix(".")
        val month = newsMonthMap[monthKey] ?: return null

        val timePieces = timePart.split(":")
        if (timePieces.size < 2) return null
        val hour = timePieces[0].toIntOrNull() ?: return null
        val minute = timePieces[1].toIntOrNull() ?: return null

        // Use local phone timezone to determine the current year
        val year = LocalDate.now(displayTimeZone).year
        
        // Parse as Poland time (the timezone from Stooq)
        val polandDateTime = LocalDateTime.of(year, month, day, hour, minute)
        val polandZoned = polandDateTime.atZone(polandTimeZone)
        
        // Convert to Instant (UTC)
        return polandZoned.toInstant()
    }

    private fun resolveNewsUrl(raw: String): String? {
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> "${StooqApi.BASE_URL}${raw.trimStart('/')}"
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
