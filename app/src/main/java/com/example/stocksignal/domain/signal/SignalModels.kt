package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalReason
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ModelScoreResult(
    val id: String,
    val score: Int,
    val reasons: List<SignalReason>
)

object RuleBasedSignalModel {

    fun compute(candles: List<PriceCandle>, range: ChartRange): ModelScoreResult? {
        if (candles.size < 20) return null
        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume.toDouble() }
        val lastClose = closes.last()
        val prevClose = closes.getOrNull(closes.lastIndex - 1) ?: lastClose

        val volumeZ = IndicatorCalculator.zScore(volumes, 20)
        val rsi = IndicatorCalculator.rsi(closes, 14)
        val macd = IndicatorCalculator.macd(closes)
        val bollinger = IndicatorCalculator.bollinger(closes, 20, 2.0)
        val atr = IndicatorCalculator.atr(candles, 14)
        val atrPercent = if (atr != null && lastClose > 0) atr / lastClose else null
        val volatilityScale = volatilityScale(atrPercent)

        val reasons = mutableListOf<SignalReason>()
        val metricScores = mutableMapOf<String, Int>()

        val (fastPeriod, slowPeriod) = maPeriodsForRange(range)
        val fast = IndicatorCalculator.sma(closes, fastPeriod)
        val slow = IndicatorCalculator.sma(closes, slowPeriod)
        val prevFast = IndicatorCalculator.sma(closes.dropLast(1), fastPeriod)
        val prevSlow = IndicatorCalculator.sma(closes.dropLast(1), slowPeriod)
        if (fast != null && slow != null && prevFast != null && prevSlow != null) {
            var maScore = 0.0
            if (prevFast <= prevSlow && fast > slow) {
                val diffPct = ((fast - slow) / slow) * 100.0
                val strength = 30.0 + min(30.0, diffPct * 2)
                val volumeBoost = volumeBoost(volumeZ)
                maScore = min(60.0, strength + volumeBoost)
                reasons.add(
                    reason(
                        id = "ma_crossover_bull",
                        title = "Moving averages crossed up",
                        explanation = "The short-term average moved above the long-term average.",
                        impact = maScore,
                        model = "ma"
                    )
                )
            } else if (prevFast >= prevSlow && fast < slow) {
                val diffPct = ((slow - fast) / slow) * 100.0
                val strength = 30.0 + min(30.0, diffPct * 2)
                val volumeBoost = volumeBoost(volumeZ)
                maScore = -min(60.0, strength + volumeBoost)
                reasons.add(
                    reason(
                        id = "ma_crossover_bear",
                        title = "Moving averages crossed down",
                        explanation = "The short-term average moved below the long-term average.",
                        impact = maScore,
                        model = "ma"
                    )
                )
            }
            val scaledMaScore = (maScore * volatilityScale).roundToInt().coerceIn(-100, 100)
            metricScores["ma"] = scaledMaScore
        }

        if (rsi != null) {
            var rsiScore = 0.0
            if (rsi < 30) {
                val distance = 30.0 - rsi
                rsiScore = min(40.0, (distance / 15.0) * 40.0)
                reasons.add(
                    reason(
                        id = "rsi_oversold",
                        title = "RSI oversold",
                        explanation = "RSI ${rsi.roundToInt()} suggests the stock is oversold.",
                        impact = rsiScore,
                        model = "rsi"
                    )
                )
            } else if (rsi > 70) {
                val distance = rsi - 70.0
                rsiScore = -min(40.0, (distance / 15.0) * 40.0)
                reasons.add(
                    reason(
                        id = "rsi_overbought",
                        title = "RSI overbought",
                        explanation = "RSI ${rsi.roundToInt()} suggests the stock is overbought.",
                        impact = rsiScore,
                        model = "rsi"
                    )
                )
            }
            val scaledRsiScore = (rsiScore * volatilityScale).roundToInt().coerceIn(-100, 100)
            metricScores["rsi"] = scaledRsiScore
        }

        if (macd != null && macd.prevHistogram != null) {
            val macdDiff = macd.macd - macd.signal
            val momentumUp = macd.histogram > macd.prevHistogram
            val momentumDown = macd.histogram < macd.prevHistogram
            val magnitude = min(30.0, abs(macdDiff / lastClose) * 1000 + 5)
            var macdScore = 0.0
            if (macdDiff > 0 && momentumUp) {
                macdScore = magnitude
                reasons.add(
                    reason(
                        id = "macd_bull",
                        title = "MACD bullish cross",
                        explanation = "MACD is above its signal line and momentum is rising.",
                        impact = macdScore,
                        model = "macd"
                    )
                )
            } else if (macdDiff < 0 && momentumDown) {
                macdScore = -magnitude
                reasons.add(
                    reason(
                        id = "macd_bear",
                        title = "MACD bearish cross",
                        explanation = "MACD is below its signal line and momentum is falling.",
                        impact = macdScore,
                        model = "macd"
                    )
                )
            }
            val scaledMacdScore = (macdScore * volatilityScale).roundToInt().coerceIn(-100, 100)
            metricScores["macd"] = scaledMacdScore
        }

        if (bollinger != null) {
            var bbScore = 0.0
            if (lastClose > bollinger.upper) {
                bbScore = min(30.0, 15.0 + volumeBoost(volumeZ))
                reasons.add(
                    reason(
                        id = "bb_breakout_up",
                        title = "Bollinger band breakout",
                        explanation = "Price closed above the upper band, showing momentum.",
                        impact = bbScore,
                        model = "bb"
                    )
                )
            } else if (lastClose < bollinger.lower) {
                bbScore = -min(30.0, 15.0 + volumeBoost(volumeZ))
                reasons.add(
                    reason(
                        id = "bb_breakout_down",
                        title = "Bollinger band breakdown",
                        explanation = "Price closed below the lower band, showing weakness.",
                        impact = bbScore,
                        model = "bb"
                    )
                )
            }
            val scaledBbScore = (bbScore * volatilityScale).roundToInt().coerceIn(-100, 100)
            metricScores["bb"] = scaledBbScore
        }

        val priceChangePct = if (prevClose == 0.0) 0.0 else ((lastClose - prevClose) / prevClose) * 100.0
        if (volumeZ != null && abs(priceChangePct) >= 3.0 && volumeZ >= 2.0) {
            val rawImpact = abs(priceChangePct) * 2 + (volumeZ - 2.0) * 5
            val volMomentumScore = min(40.0, rawImpact) * if (priceChangePct > 0) 1 else -1
            reasons.add(
                reason(
                    id = "volume_momentum",
                    title = "Volume spike + price move",
                    explanation = "Volume spiked with a ${"%.1f".format(priceChangePct)}% move.",
                    impact = volMomentumScore,
                    model = "volume"
                )
            )
            val scaledVolScore = (volMomentumScore * volatilityScale).roundToInt().coerceIn(-100, 100)
            metricScores["volume"] = scaledVolScore
        }

        if (candles.size >= 21) {
            val previous = closes.dropLast(1).takeLast(20)
            val prevHigh = previous.maxOrNull()
            val prevLow = previous.minOrNull()
            var breakoutScore = 0.0
            if (prevHigh != null && lastClose > prevHigh && (volumeZ ?: 0.0) >= 1.5) {
                breakoutScore = min(35.0, 25.0 + max(0.0, (volumeZ ?: 0.0) - 1.5) * 5)
                reasons.add(
                    reason(
                        id = "breakout_up",
                        title = "Breakout above recent high",
                        explanation = "Price cleared the 20-day high with supportive volume.",
                        impact = breakoutScore,
                        model = "breakout"
                    )
                )
            } else if (prevLow != null && lastClose < prevLow && (volumeZ ?: 0.0) >= 1.5) {
                breakoutScore = -min(35.0, 25.0 + max(0.0, (volumeZ ?: 0.0) - 1.5) * 5)
                reasons.add(
                    reason(
                        id = "breakout_down",
                        title = "Breakdown below support",
                        explanation = "Price fell below the 20-day low with heavy volume.",
                        impact = breakoutScore,
                        model = "breakout"
                    )
                )
            }
            val scaledBreakoutScore = (breakoutScore * volatilityScale).roundToInt().coerceIn(-100, 100)
            metricScores["breakout"] = scaledBreakoutScore
        }

        // Add rolling z-score metric
        if (candles.size >= 21) {
            val returnZScore = IndicatorCalculator.returnZScore(closes, 20)
            if (returnZScore != null) {
                val rawZScore = (returnZScore * 20.0).coerceIn(-60.0, 60.0)
                val zScoreImpact = rawZScore
                reasons.add(
                    reason(
                        id = "return_zscore",
                        title = "Return anomaly",
                        explanation = "Latest return z-score is ${"%.2f".format(returnZScore)}.",
                        impact = zScoreImpact,
                        model = "zscore"
                    )
                )
                val scaledZScore = (zScoreImpact * volatilityScale).roundToInt().coerceIn(-100, 100)
                metricScores["zscore"] = scaledZScore
            }
        }

        if (metricScores.isEmpty()) return null
        
        // Average score across all metrics
        val avgScore = metricScores.values.average().roundToInt()
        
        return ModelScoreResult(
            id = "unified",
            score = avgScore,
            reasons = reasons
        )
    }

    private fun maPeriodsForRange(range: ChartRange): Pair<Int, Int> {
        return when (range) {
            ChartRange.ONE_DAY -> 5 to 20
            ChartRange.FIVE_DAY, ChartRange.ONE_MONTH -> 20 to 50
            ChartRange.SIX_MONTH, ChartRange.ONE_YEAR, ChartRange.FIVE_YEAR -> 50 to 200
        }
    }

    private fun volumeBoost(volumeZ: Double?): Double {
        if (volumeZ == null) return 0.0
        return (volumeZ - 1.5).coerceIn(0.0, 1.5) * 8.0
    }

    private fun volatilityScale(atrPercent: Double?): Double {
        if (atrPercent == null) return 1.0
        return when {
            atrPercent < 0.002 -> 0.6
            atrPercent < 0.005 -> 0.8
            else -> 1.0
        }
    }

    private fun reason(
        id: String,
        title: String,
        explanation: String,
        impact: Double,
        model: String?
    ): SignalReason {
        return SignalReason(
            id = id,
            title = title,
            explanation = explanation,
            impactScore = impact.roundToInt(),
            model = model
        )
    }
}
