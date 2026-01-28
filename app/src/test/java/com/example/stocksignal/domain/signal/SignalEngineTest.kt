package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

class SignalEngineTest {

    @Test
    fun `aggregation uses average and mode with confidence formula`() {
        val returnWindow = IndicatorConfig.forHoldingPeriod(
            com.example.stocksignal.data.settings.HoldingPeriod.MONTHS
        ).rollingReturnZScoreWindow
        val candles = sampleCandles(returnWindow + 1)
        val signal = SignalEngine.computeSignal(candles, ChartRange.ONE_MONTH)
        assertNotNull(signal)
        val result = signal!!

        val model = RuleBasedSignalModel.compute(candles, ChartRange.ONE_MONTH)
        assertNotNull(model)
        
        // Extract metric scores from reasons
        val metricScores = mutableMapOf<String, Int>()
        model!!.reasons.forEach { reason ->
            reason.model?.let { metricName ->
                metricScores[metricName] = reason.impactScore
            }
        }
        
        val atr = IndicatorCalculator.atr(candles, 14)
        val lastClose = candles.last().close
        val atrPercent = if (atr != null && lastClose > 0) atr / lastClose else null
        
        val scores = metricScores.values.toList()
        val expectedAverage = scores.average().roundToInt()
        val expectedMode = scoreMode(scores)
        val expectedFinal = (expectedMode ?: expectedAverage).coerceIn(-100, 100)

        assertEquals(expectedAverage, result.averageScore)
        assertEquals(expectedMode, result.modeScore)
        assertEquals(expectedFinal, result.score)

        val expectedConfidence = confidenceScore(expectedAverage, scores, atrPercent)
        assertEquals(expectedConfidence, result.confidence)
    }

    private fun sampleCandles(count: Int): List<PriceCandle> {
        val start = LocalDateTime.of(2024, 1, 1, 9, 30)
        return List(count) { index ->
            val base = 100.0 + (index % 2) * 0.4
            val close = base + if (index % 4 == 0) 0.1 else -0.1
            PriceCandle(
                time = start.plusMinutes(index.toLong()),
                open = close - 0.05,
                high = close + 0.15,
                low = close - 0.15,
                close = close,
                volume = 1_000L + (index * 3)
            )
        }
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
}
