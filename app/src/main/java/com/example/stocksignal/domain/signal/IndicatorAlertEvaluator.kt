package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.PriceCandle

object IndicatorAlertEvaluator {

    data class Evaluation(
        val current: Double,
        val previous: Double?,
        val crossed: Boolean
    )

    fun evaluate(
        alert: IndicatorAlertSetting,
        candles: List<PriceCandle>
    ): Evaluation? {
        if (candles.size < 2) return null
        val closes = candles.map { it.close }
        val (current, previous) = when (alert.metric) {
            IndicatorMetric.RSI_14 -> valueWithPrevious(
                current = IndicatorCalculator.rsi(closes, 14),
                previous = IndicatorCalculator.rsi(closes.dropLast(1), 14)
            )
            IndicatorMetric.MACD_HISTOGRAM -> {
                val snapshot = IndicatorCalculator.macd(closes) ?: return null
                val prev = snapshot.prevHistogram ?: IndicatorCalculator.macd(closes.dropLast(1))?.histogram
                valueWithPrevious(snapshot.histogram, prev)
            }
            IndicatorMetric.MACD_LINE -> {
                val snapshot = IndicatorCalculator.macd(closes) ?: return null
                val prev = IndicatorCalculator.macd(closes.dropLast(1))?.macd
                valueWithPrevious(snapshot.macd, prev)
            }
            IndicatorMetric.SMA_50_DISTANCE -> smaDistance(closes, 50)
            IndicatorMetric.SMA_200_DISTANCE -> smaDistance(closes, 200)
            IndicatorMetric.BOLLINGER_PERCENT_B -> bollingerPercentB(closes)
            IndicatorMetric.ATR_PERCENT -> atrPercent(candles)
            IndicatorMetric.RETURN_ZSCORE_20 -> valueWithPrevious(
                current = IndicatorCalculator.returnZScore(closes, 20),
                previous = IndicatorCalculator.returnZScore(closes.dropLast(1), 20)
            )
        } ?: return null

        if (!current.isFinite()) return null
        val crossed = when (alert.direction) {
            AlertDirection.ABOVE -> previous != null && previous <= alert.threshold && current > alert.threshold
            AlertDirection.BELOW -> previous != null && previous >= alert.threshold && current < alert.threshold
        }
        return Evaluation(current = current, previous = previous, crossed = crossed)
    }

    private fun valueWithPrevious(
        current: Double?,
        previous: Double?
    ): Pair<Double, Double?>? {
        val currentValue = current ?: return null
        return currentValue to previous
    }

    private fun smaDistance(closes: List<Double>, period: Int): Pair<Double, Double?>? {
        val sma = IndicatorCalculator.sma(closes, period) ?: return null
        val last = closes.lastOrNull() ?: return null
        val current = (last - sma) / sma * 100.0
        val prevCloses = closes.dropLast(1)
        val prevSma = IndicatorCalculator.sma(prevCloses, period)
        val prevClose = prevCloses.lastOrNull()
        val previous = if (prevSma != null && prevClose != null) {
            (prevClose - prevSma) / prevSma * 100.0
        } else {
            null
        }
        return current to previous
    }

    private fun bollingerPercentB(closes: List<Double>): Pair<Double, Double?>? {
        val bands = IndicatorCalculator.bollinger(closes) ?: return null
        val last = closes.lastOrNull() ?: return null
        val range = bands.upper - bands.lower
        if (range == 0.0) return null
        val current = ((last - bands.lower) / range) * 100.0
        val prevCloses = closes.dropLast(1)
        val prevBands = IndicatorCalculator.bollinger(prevCloses) ?: return current to null
        val prevClose = prevCloses.lastOrNull() ?: return current to null
        val prevRange = prevBands.upper - prevBands.lower
        val previous = if (prevRange == 0.0) null else ((prevClose - prevBands.lower) / prevRange) * 100.0
        return current to previous
    }

    private fun atrPercent(candles: List<PriceCandle>): Pair<Double, Double?>? {
        val atr = IndicatorCalculator.atr(candles, 14) ?: return null
        val last = candles.lastOrNull()?.close ?: return null
        if (last == 0.0) return null
        val current = (atr / last) * 100.0
        val prevCandles = candles.dropLast(1)
        val prevAtr = IndicatorCalculator.atr(prevCandles, 14)
        val prevClose = prevCandles.lastOrNull()?.close
        val previous = if (prevAtr != null && prevClose != null && prevClose != 0.0) {
            (prevAtr / prevClose) * 100.0
        } else {
            null
        }
        return current to previous
    }
}
