package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.PriceCandle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object IndicatorCalculator {

    data class MacdSnapshot(
        val macd: Double,
        val signal: Double,
        val histogram: Double,
        val prevHistogram: Double?
    )

    data class BollingerBands(
        val middle: Double,
        val upper: Double,
        val lower: Double
    )

    fun sma(values: List<Double>, period: Int): Double? {
        if (values.size < period || period <= 0) return null
        return values.takeLast(period).average()
    }

    fun rsi(closes: List<Double>, period: Int): Double? {
        if (closes.size < period + 1 || period <= 0) return null
        val window = closes.takeLast(period + 1)
        var gains = 0.0
        var losses = 0.0
        for (i in 1 until window.size) {
            val diff = window[i] - window[i - 1]
            if (diff >= 0) gains += diff else losses += abs(diff)
        }
        val avgGain = gains / period
        val avgLoss = losses / period
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1 + rs))
    }

    fun emaSeries(values: List<Double>, period: Int): List<Double?> {
        if (values.isEmpty() || period <= 0) return emptyList()
        val result = MutableList<Double?>(values.size) { null }
        if (values.size < period) return result
        val k = 2.0 / (period + 1)
        val firstEma = values.take(period).average()
        result[period - 1] = firstEma
        for (i in period until values.size) {
            val prev = result[i - 1] ?: firstEma
            result[i] = (values[i] - prev) * k + prev
        }
        return result
    }

    fun macd(
        closes: List<Double>,
        shortPeriod: Int = 12,
        longPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdSnapshot? {
        if (closes.size < longPeriod + signalPeriod) return null
        val emaShort = emaSeries(closes, shortPeriod)
        val emaLong = emaSeries(closes, longPeriod)
        val macdSeries = closes.indices.mapNotNull { index ->
            val short = emaShort[index]
            val long = emaLong[index]
            if (short != null && long != null) short - long else null
        }
        if (macdSeries.size < signalPeriod + 1) return null
        val signalSeries = emaSeries(macdSeries, signalPeriod)
        val lastMacd = macdSeries.last()
        val lastSignal = signalSeries.lastOrNull { it != null } ?: return null
        val histogram = lastMacd - lastSignal
        val prevHistogram = if (macdSeries.size >= 2) {
            val prevSignal = signalSeries.getOrNull(signalSeries.size - 2)
            val prevMacd = macdSeries[macdSeries.size - 2]
            if (prevSignal != null) prevMacd - prevSignal else null
        } else {
            null
        }
        return MacdSnapshot(
            macd = lastMacd,
            signal = lastSignal,
            histogram = histogram,
            prevHistogram = prevHistogram
        )
    }

    fun bollinger(closes: List<Double>, period: Int = 20, multiplier: Double = 2.0): BollingerBands? {
        if (closes.size < period || period <= 0) return null
        val window = closes.takeLast(period)
        val mean = window.average()
        val stdDev = stdDev(window)
        return BollingerBands(
            middle = mean,
            upper = mean + multiplier * stdDev,
            lower = mean - multiplier * stdDev
        )
    }

    fun atr(candles: List<PriceCandle>, period: Int = 14): Double? {
        if (candles.size < period + 1) return null
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prev = candles[i - 1]
            val highLow = current.high - current.low
            val highClose = abs(current.high - prev.close)
            val lowClose = abs(current.low - prev.close)
            trueRanges.add(max(highLow, max(highClose, lowClose)))
        }
        return trueRanges.takeLast(period).average()
    }

    fun zScore(values: List<Double>, window: Int): Double? {
        if (values.size < window || window <= 1) return null
        val windowValues = values.takeLast(window)
        val mean = windowValues.average()
        val std = stdDev(windowValues)
        if (std == 0.0) return 0.0
        val last = windowValues.last()
        return (last - mean) / std
    }

    fun returnZScore(closes: List<Double>, window: Int): Double? {
        if (closes.size < window + 1) return null
        val returns = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val prev = closes[i - 1]
            if (prev == 0.0) continue
            returns.add((closes[i] - prev) / prev)
        }
        return zScore(returns, window)
    }

    fun stdDev(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }
}
