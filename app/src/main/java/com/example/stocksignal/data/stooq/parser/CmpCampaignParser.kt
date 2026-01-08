package com.example.stocksignal.data.stooq.parser

object CmpCampaignParser {

    private val cmpRegex = Regex("cmp/\\?(\\d+)&q=")

    fun parseCampaignId(html: String): String? {
        if (html.isBlank()) return null
        return cmpRegex.find(html)?.groupValues?.getOrNull(1)
    }
}
