package com.example.stocksignal.data.stooq.parser

import com.example.stocksignal.data.stooq.model.SearchResult

object CmpParser {

    private val rowRegex = Regex("window\\.cmp_r\\('([^']*)'\\)")

    fun parse(raw: String): List<SearchResult> {
        if (raw.isBlank()) return emptyList()
        return rowRegex.findAll(raw).mapNotNull { match ->
            val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (payload.isBlank()) return@mapNotNull null
            val fields = payload.split('~')
            val symbol = fields.getOrNull(0)?.trim().orEmpty()
            val companyName = fields.getOrNull(1)?.trim().orEmpty()
            val exchange = fields.getOrNull(2)?.trim()
            if (symbol.isBlank() || companyName.isBlank()) return@mapNotNull null
            SearchResult(
                symbol = symbol,
                companyName = companyName,
                exchange = exchange?.takeIf { it.isNotBlank() }
            )
        }.toList()
    }
}
