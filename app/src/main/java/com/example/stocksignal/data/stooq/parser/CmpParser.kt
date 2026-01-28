package com.example.stocksignal.data.stooq.parser

import android.util.Log
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.util.HtmlUtils

object CmpParser {

    private const val TAG = "CmpParser"

    // Updated regex to handle escaped single quotes within the payload
    private val rowRegex = Regex("window\\.cmp_r\\('(.*?)'\\);")

    fun parse(raw: String): List<SearchResult> {
        if (raw.isBlank()) return emptyList()
        
        val allResults = mutableListOf<SearchResult>()
        
        rowRegex.findAll(raw).forEach { match ->
            val payload = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (payload.isBlank()) return@forEach
            
            // Split by pipe to get individual results
            val entries = payload.split('|')
            
            entries.forEach { entry ->
                val fields = entry.split('~')
                val rawSymbol = fields.getOrNull(0)?.trim().orEmpty()
                val rawCompanyName = fields.getOrNull(1)?.trim().orEmpty()
                val exchange = fields.getOrNull(2)?.trim()
                val price = fields.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                val percentChange = fields.getOrNull(4)
                    ?.trim()
                    ?.removeSuffix("%")
                    ?.takeIf { it.isNotBlank() }
                    ?.toDoubleOrNull()
                
                // Strip HTML tags and decode entities from symbol and company name
                // Also unescape single quotes (JavaScript escaped quotes)
                val symbol = HtmlUtils.stripHtml(rawSymbol).replace("\\'", "'")
                val companyName = HtmlUtils.stripHtml(rawCompanyName).replace("\\'", "'")
                
                if (symbol.isBlank() || companyName.isBlank()) {
                    Log.d(TAG, "Skipping entry with blank symbol or name: rawSymbol=$rawSymbol, rawCompanyName=$rawCompanyName")
                    return@forEach
                }
                
                // Filter: only include results that either:
                // 1. Don't have a period in the symbol, OR
                // 2. End with .US or .us
                val shouldInclude = !symbol.contains('.') || 
                                  symbol.endsWith(".US", ignoreCase = true)
                
                if (shouldInclude) {
                    allResults.add(
                        SearchResult(
                            symbol = symbol,
                            companyName = companyName,
                            exchange = exchange?.takeIf { it.isNotBlank() },
                            price = price,
                            percentChange = percentChange
                        )
                    )
                } else {
                    Log.d(TAG, "Filtered out: $symbol (does not match .US filter)")
                }
            }
        }
        
        Log.i(TAG, "Parsed ${allResults.size} filtered search results from raw response")
        return allResults
    }
}
