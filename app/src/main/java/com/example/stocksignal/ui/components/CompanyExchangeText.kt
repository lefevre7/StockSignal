package com.example.stocksignal.ui.components

import android.text.TextUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

private val exchangeSuffixRegex = Regex("\\s*\\*\\s*([A-Za-z0-9.]+)\\s*$")

private val exchangeMap = mapOf(
    "XNAS" to "NASDAQ",
    "XNYS" to "NYSE",
    "XASE" to "NYSE American",
    "ARCX" to "NYSE Arca",
    "BATS" to "Cboe BZX",
    "EDGX" to "Cboe EDGX",
    "EDGA" to "Cboe EDGA",
    "IEX" to "IEX"
)

@Composable
fun CompanyExchangeText(
    companyName: String?,
    exchange: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxLines: Int = 1
) {
    val display = remember(companyName, exchange) {
        formatCompanyExchange(companyName, exchange)
    }
    if (display.companyHtml.isBlank() && display.exchange == null) return

    val html = buildString {
        if (display.companyHtml.isNotBlank()) {
            append(display.companyHtml)
        }
        display.exchange?.let { exch ->
            if (display.companyHtml.isNotBlank()) {
                append(" &bull; ")
            }
            append(TextUtils.htmlEncode(exch))
        }
    }

    HtmlText(
        html = html,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

private data class CompanyExchangeDisplay(
    val companyHtml: String,
    val exchange: String?
)

private fun formatCompanyExchange(
    companyName: String?,
    exchange: String?
): CompanyExchangeDisplay {
    var resolvedExchange = exchange?.trim().takeIf { !it.isNullOrBlank() }
    var name = companyName?.trim().orEmpty()
    val match = exchangeSuffixRegex.find(name)
    if (match != null) {
        val code = match.groupValues.getOrNull(1)
        if (resolvedExchange.isNullOrBlank() && !code.isNullOrBlank()) {
            resolvedExchange = code
        }
        name = name.removeRange(match.range).trim()
    }
    val friendly = resolvedExchange?.let { code ->
        exchangeMap[code.uppercase()] ?: code
    }
    return CompanyExchangeDisplay(companyHtml = name, exchange = friendly)
}
