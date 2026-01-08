package com.example.stocksignal.data.stooq.model

enum class MarketMoverRange(val label: String) {
    ONE_DAY("1D"),
    FIVE_DAY("5D"),
    ONE_MONTH("1M"),
    SIX_MONTH("6M"),
    ONE_YEAR("1Y"),
    FIVE_YEAR("5Y");

    companion object {
        fun fromText(text: String): MarketMoverRange? {
            val normalized = text.lowercase()
            return when {
                normalized.contains("1d") || normalized.contains("1 day") || normalized.contains("daily") -> ONE_DAY
                normalized.contains("5d") || normalized.contains("5 day") -> FIVE_DAY
                normalized.contains("1m") || normalized.contains("1 month") -> ONE_MONTH
                normalized.contains("6m") || normalized.contains("6 month") -> SIX_MONTH
                normalized.contains("1y") || normalized.contains("1 year") -> ONE_YEAR
                normalized.contains("5y") || normalized.contains("5 year") -> FIVE_YEAR
                else -> null
            }
        }
    }
}

enum class MarketMoverDirection {
    INCREASERS,
    DECREASERS;

    companion object {
        fun fromText(text: String): MarketMoverDirection? {
            val normalized = text.lowercase()
            return when {
                normalized.contains("increaser") ||
                    normalized.contains("gainer") ||
                    normalized.contains("rising") ||
                    normalized.contains("up") ->
                    INCREASERS
                normalized.contains("decreaser") ||
                    normalized.contains("loser") ||
                    normalized.contains("falling") ||
                    normalized.contains("down") ->
                    DECREASERS
                else -> null
            }
        }
    }
}

data class MarketMoversSection(
    val range: MarketMoverRange?,
    val direction: MarketMoverDirection?,
    val items: List<com.example.stocksignal.data.local.model.MarketMoverItem>
)
