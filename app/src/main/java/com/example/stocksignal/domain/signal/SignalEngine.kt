package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalResult
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

object SignalEngine {

    fun computeSignal(
        candles: List<PriceCandle>,
        range: ChartRange,
        holdingPeriod: com.example.stocksignal.data.settings.HoldingPeriod = com.example.stocksignal.data.settings.HoldingPeriod.MONTHS,
        generatedAt: LocalDateTime = LocalDateTime.now()
    ): SignalResult? {
        if (candles.size < 5) return null
        val atrPercent = atrPercent(candles)

        val model = RuleBasedSignalModel.compute(candles, range, holdingPeriod) ?: return null
        
        // Get individual metric scores from the reasons
        val metricScores = mutableMapOf<String, Int>()
        model.reasons.forEach { reason ->
            reason.model?.let { metricName ->
                // Use the impact score as the metric score
                metricScores[metricName] = reason.impactScore
            }
        }
        
        if (metricScores.isEmpty()) return null

        val averageScore = metricScores.values.average().roundToInt()
        val modeScore = scoreMode(metricScores.values.toList())
        val finalScore = modeScore ?: averageScore

        val reasons = model.reasons
            .sortedByDescending { abs(it.impactScore) }
            .take(3)

        val confidence = confidenceScore(
            averageScore = averageScore,
            modelScores = metricScores.values.toList(),
            atrPercent = atrPercent
        )

        return SignalResult(
            score = finalScore.coerceIn(-100, 100),
            averageScore = averageScore.coerceIn(-100, 100),
            modeScore = modeScore?.coerceIn(-100, 100),
            confidence = confidence,
            reasons = reasons,
            modelScores = metricScores,
            generatedAt = generatedAt
        )
    }

    private fun scoreMode(scores: List<Int>): Int? {
        if (scores.isEmpty()) return null
        val buckets = scores.groupingBy { roundToNearestFive(it) }.eachCount()
        val maxCount = buckets.values.maxOrNull() ?: return null
        val topBuckets = buckets.filterValues { it == maxCount }.keys
        if (maxCount == 1 || topBuckets.size != 1) return null
        return topBuckets.first()
    }

    private fun roundToNearestFive(value: Int): Int {
        return (value / 5.0).roundToInt() * 5
    }

    private fun confidenceScore(
        averageScore: Int,
        modelScores: List<Int>,
        atrPercent: Double?
    ): Int {
        if (modelScores.isEmpty()) return 50
        val avgSign = scoreSign(averageScore)
        val agreement = modelScores.count { scoreSign(it) == avgSign }.toDouble() / modelScores.size
        val volNorm = ((atrPercent ?: 0.025) / 0.05).coerceIn(0.0, 1.0)
        val confidence = (
            0.5 * (abs(averageScore) / 100.0) +
                0.4 * agreement +
                0.1 * (1 - volNorm)
            ) * 100.0
        return confidence.roundToInt().coerceIn(0, 100)
    }

    private fun scoreSign(value: Int): Int {
        return when {
            value >= 5 -> 1
            value <= -5 -> -1
            else -> 0
        }
    }

    private fun atrPercent(candles: List<PriceCandle>): Double? {
        val lastClose = candles.lastOrNull()?.close ?: return null
        if (lastClose <= 0.0) return null
        val atr = IndicatorCalculator.atr(candles, 14) ?: return null
        return atr / lastClose
    }

    private fun volatilityScale(atrPercent: Double?): Double {
        if (atrPercent == null) return 1.0
        return when {
            atrPercent < 0.002 -> 0.6
            atrPercent < 0.005 -> 0.8
            else -> 1.0
        }
    }
}
