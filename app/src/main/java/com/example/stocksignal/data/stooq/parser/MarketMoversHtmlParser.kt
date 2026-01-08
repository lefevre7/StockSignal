package com.example.stocksignal.data.stooq.parser

import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.MarketMoversSection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object MarketMoversHtmlParser {

    private val percentRegex = Regex("([+-]?\\d+(?:\\.\\d+)?)%")
    private val numberRegex = Regex("([+-]?\\d+(?:\\.\\d+)?)")

    fun parse(html: String): List<MarketMoversSection> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val tables = doc.select("table")
        val sections = mutableListOf<MarketMoversSection>()

        tables.forEach { table ->
            val items = parseTable(table)
            if (items.isEmpty()) return@forEach
            val contextText = buildContextText(table)
            val range = MarketMoverRange.fromText(contextText)
            val direction = MarketMoverDirection.fromText(contextText)
            sections.add(
                MarketMoversSection(
                    range = range,
                    direction = direction,
                    items = items
                )
            )
        }
        return sections
    }

    private fun parseTable(table: Element): List<MarketMoverItem> {
        val rows = table.select("tr")
        val items = mutableListOf<MarketMoverItem>()

        rows.forEachIndexed { index, row ->
            val link = row.selectFirst("a[href*=q/?s=]") ?: return@forEachIndexed
            val symbol = link.text().trim().ifBlank { extractSymbol(link.attr("href")) }
            if (symbol.isBlank()) return@forEachIndexed
            val cells = row.select("td")
            val tickerCell = link.parent()
            val companyCell = tickerCell?.nextElementSibling()
            val companyName = companyCell?.text()?.trim().orEmpty()
            if (companyName.isBlank()) return@forEachIndexed
            val cellTexts = cells.map { it.text().trim() }
            val percentChange = cellTexts.firstNotNullOfOrNull { text ->
                percentRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            }
            val price = cellTexts.firstNotNullOfOrNull { text ->
                if (text.contains('%')) return@firstNotNullOfOrNull null
                numberRegex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            }
            items.add(
                MarketMoverItem(
                    ticker = symbol,
                    companyName = companyName,
                    exchange = null,
                    price = price,
                    percentChange = percentChange,
                    rank = index + 1,
                    signalScore = null,
                    signalLabel = null
                )
            )
        }
        return items
    }

    private fun buildContextText(table: Element): String {
        val builder = StringBuilder()
        builder.append(table.id()).append(' ')
        builder.append(table.classNames().joinToString(" ")).append(' ')

        var current: Element? = table
        var depth = 0
        while (current != null && depth < 4) {
            current.previousElementSibling()?.let { sibling ->
                if (sibling.tagName().matches(Regex("h[1-6]")) || sibling.tagName() == "div") {
                    builder.append(sibling.text()).append(' ')
                }
            }
            current = current.parent()
            depth++
        }
        return builder.toString()
    }

    private fun extractSymbol(href: String): String {
        val queryIndex = href.indexOf("s=")
        if (queryIndex == -1) return ""
        return href.substring(queryIndex + 2).takeWhile { it != '&' }.trim()
    }
}
