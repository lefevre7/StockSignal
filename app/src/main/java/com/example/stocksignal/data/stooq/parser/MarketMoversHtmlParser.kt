package com.example.stocksignal.data.stooq.parser

import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.MarketMoversSection
import com.example.stocksignal.util.HtmlUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Locale

object MarketMoversHtmlParser {

    private val percentRegex = Regex("([+-]?\\d+(?:[\\.,]\\d+)?)%")
    private val numberRegex = Regex("([+-]?\\d+(?:[\\.,]\\d+)?)")
    private val mostActiveRegex = Regex("(?i)Najbardziej aktywne|Most active")

    fun parse(html: String): List<MarketMoversSection> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val headingElement = doc.selectFirst("b:matchesOwn(${mostActiveRegex.pattern})")
            ?: doc.selectFirst("td:matchesOwn(${mostActiveRegex.pattern})")
            ?: return emptyList()
        val table = headingElement.closest("table") ?: return emptyList()
        val itemsByDirection = linkedMapOf<MarketMoverDirection, MutableList<MarketMoverItem>>()
        var currentDirection: MarketMoverDirection? = null

        table.select("> tbody > tr, > tr").forEach { row ->
            val headingDirection = headingDirection(row)
            if (headingDirection != null) {
                currentDirection = headingDirection
                return@forEach
            }

            val link = row.selectFirst("a[href*=q/?s=]") ?: return@forEach
            val direction = currentDirection ?: return@forEach
            val rawSymbol = parseSymbol(link)
            if (rawSymbol.isBlank()) return@forEach

            val cells = row.select("td")
            val rawCompanyName = cells.getOrNull(1)?.text()?.trim().orEmpty()
            if (rawCompanyName.isBlank()) return@forEach
            
            // Strip HTML tags and decode entities from symbol and company name
            val symbol = HtmlUtils.stripHtml(rawSymbol)
            val companyName = HtmlUtils.stripHtml(rawCompanyName)
            if (symbol.isBlank() || companyName.isBlank()) return@forEach

            val price = parseNumber(cells.getOrNull(2)?.text().orEmpty())
            val percentChange = parsePercent(cells.getOrNull(3)?.text().orEmpty())

            val list = itemsByDirection.getOrPut(direction) { mutableListOf() }
            val rank = list.size + 1
            list.add(
                MarketMoverItem(
                    ticker = symbol,
                    companyName = companyName,
                    exchange = null,
                    price = price,
                    percentChange = percentChange,
                    rank = rank,
                    signalScore = null,
                    signalLabel = null
                )
            )
        }

        val sections = mutableListOf<MarketMoversSection>()
        val orderedDirections = listOf(
            MarketMoverDirection.MOST_ACTIVE,
            MarketMoverDirection.INCREASERS,
            MarketMoverDirection.DECREASERS
        )
        orderedDirections.forEach { direction ->
            val items = itemsByDirection[direction].orEmpty()
            if (items.isNotEmpty()) {
                sections.add(
                    MarketMoversSection(
                        range = MarketMoverRange.ONE_DAY,
                        direction = direction,
                        items = items
                    )
                )
            }
        }
        return sections
    }

    private fun headingDirection(row: Element): MarketMoverDirection? {
        val text = row.text().trim()
        if (text.isBlank()) return null
        val direction = MarketMoverDirection.fromText(text) ?: return null
        val hasHeaderCell = row.selectFirst("td[colspan]") != null || row.selectFirst("b") != null
        return if (hasHeaderCell) direction else null
    }

    private fun parseSymbol(link: Element): String {
        val raw = link.text().trim().ifBlank { extractSymbol(link.attr("href")) }
        return raw.trim().uppercase(Locale.US)
    }

    private fun parsePercent(text: String): Double? {
        val normalized = text.replace(',', '.')
        return percentRegex.find(normalized)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun parseNumber(text: String): Double? {
        val normalized = text.replace(',', '.')
        return numberRegex.find(normalized)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun extractSymbol(href: String): String {
        val queryIndex = href.indexOf("s=")
        if (queryIndex == -1) return ""
        return href.substring(queryIndex + 2).takeWhile { it != '&' }.trim()
    }
}
