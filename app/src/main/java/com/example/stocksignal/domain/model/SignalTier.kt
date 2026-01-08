package com.example.stocksignal.domain.model

enum class SignalTier(
    val label: String,
    val summary: String,
    val minScore: Int,
    val maxScore: Int
) {
    STRONG_BUY("Strong Buy", "Really good time to buy", 60, 100),
    BUY("Buy", "Ok time to buy", 30, 59),
    NEUTRAL("Hold", "Neutral", -29, 29),
    SELL("Sell", "Ok time to sell", -59, -30),
    STRONG_SELL("Strong Sell", "Really good time to sell", -100, -60);

    companion object {
        fun fromScore(score: Int): SignalTier {
            return when {
                score >= STRONG_BUY.minScore -> STRONG_BUY
                score >= BUY.minScore -> BUY
                score <= STRONG_SELL.maxScore -> STRONG_SELL
                score <= SELL.maxScore -> SELL
                else -> NEUTRAL
            }
        }
    }
}
