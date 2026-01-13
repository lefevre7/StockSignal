package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import java.time.LocalDateTime
import kotlin.math.sin

/**
 * Builder for creating realistic test PriceCandles with various patterns
 */
class PriceCandleBuilder {
    private var startTime: LocalDateTime = LocalDateTime.of(2024, 1, 1, 9, 30)
    private var basePrice: Double = 100.0
    private var pattern: PricePattern = PricePattern.FLAT
    private var volatility: Double = 0.02 // 2% default
    private var volumeBase: Long = 1_000_000L
    private var count: Int = 100

    fun startingAt(time: LocalDateTime) = apply { this.startTime = time }
    fun basePrice(price: Double) = apply { this.basePrice = price }
    fun pattern(pattern: PricePattern) = apply { this.pattern = pattern }
    fun volatility(vol: Double) = apply { this.volatility = vol }
    fun volume(vol: Long) = apply { this.volumeBase = vol }
    fun count(n: Int) = apply { this.count = n }

    fun build(): List<PriceCandle> {
        return List(count) { index ->
            val time = when {
                count <= 390 -> startTime.plusMinutes(index.toLong()) // Intraday (1-minute bars)
                count <= 2000 -> startTime.plusDays(index.toLong()) // Daily
                else -> startTime.plusWeeks(index.toLong()) // Weekly/Monthly
            }

            val trendFactor = when (pattern) {
                PricePattern.FLAT -> 0.0
                PricePattern.TRENDING_UP -> index * 0.005
                PricePattern.TRENDING_DOWN -> -index * 0.005
                PricePattern.VOLATILE -> (index % 5 - 2.5) * 0.02
                PricePattern.GAP_UP -> if (index == count / 2) 0.05 else 0.0
                PricePattern.GAP_DOWN -> if (index == count / 2) -0.05 else 0.0
                PricePattern.SIDEWAYS -> sin(index * 0.2) * 0.01
                PricePattern.STRONG_UPTREND -> index * 0.01
                PricePattern.STRONG_DOWNTREND -> -index * 0.01
                PricePattern.REVERSAL_UP -> if (index < count / 2) -0.005 * index else 0.01 * (index - count / 2)
                PricePattern.REVERSAL_DOWN -> if (index < count / 2) 0.005 * index else -0.01 * (index - count / 2)
                PricePattern.VOLUME_SPIKE -> 0.0
            }

            val noise = (Math.random() - 0.5) * volatility
            val close = basePrice * (1 + trendFactor + noise)
            val high = close * (1 + volatility * Math.random())
            val low = close * (1 - volatility * Math.random())
            val open = (high + low) / 2

            val volumeNoise = (Math.random() - 0.5) * 0.3
            val volumeSpike = if (pattern == PricePattern.VOLUME_SPIKE && index % 10 == 0) 3.0 else 1.0
            val volume = (volumeBase * (1 + volumeNoise) * volumeSpike).toLong()

            PriceCandle(
                time = time,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume
            )
        }
    }

    enum class PricePattern {
        FLAT,
        TRENDING_UP,
        TRENDING_DOWN,
        VOLATILE,
        GAP_UP,
        GAP_DOWN,
        SIDEWAYS,
        STRONG_UPTREND,
        STRONG_DOWNTREND,
        REVERSAL_UP,
        REVERSAL_DOWN,
        VOLUME_SPIKE
    }
}

/**
 * Builder for IndicatorAlertSetting
 */
class IndicatorAlertSettingBuilder {
    private var metric: IndicatorMetric = IndicatorMetric.RSI_14
    private var threshold: Double = 30.0
    private var direction: AlertDirection = AlertDirection.BELOW
    private var enabled: Boolean = true

    fun metric(m: IndicatorMetric) = apply { this.metric = m }
    fun threshold(t: Double) = apply { this.threshold = t }
    fun direction(d: AlertDirection) = apply { this.direction = d }
    fun enabled(e: Boolean) = apply { this.enabled = e }

    fun build() = IndicatorAlertSetting(
        metric = metric,
        threshold = threshold,
        direction = direction,
        enabled = enabled
    )
}

/**
 * Builder for WatchlistItemEntity for testing
 */
class WatchlistItemBuilder {
    private var symbol: String = "AAPL.US"
    private var companyName: String = "Apple Inc."
    private var exchange: String = "NASDAQ"
    private var addedAt: LocalDateTime = LocalDateTime.now()
    private var alertEnabled: Boolean = true
    private var minScoreForNotify: Int = 60
    private var indicatorAlertsJson: String? = null
    private var tags: List<String> = emptyList()

    fun symbol(s: String) = apply { this.symbol = s }
    fun companyName(name: String) = apply { this.companyName = name }
    fun alertEnabled(enabled: Boolean) = apply { this.alertEnabled = enabled }
    fun minScore(score: Int) = apply { this.minScoreForNotify = score }
    fun indicatorAlerts(json: String?) = apply { this.indicatorAlertsJson = json }
    fun tags(t: List<String>) = apply { this.tags = t }

    fun build() = WatchlistItemEntity(
        symbol = symbol,
        companyName = companyName,
        exchange = exchange,
        addedAt = addedAt,
        alertEnabled = alertEnabled,
        minScoreForNotify = minScoreForNotify,
        quietHoursStart = null,
        quietHoursEnd = null,
        snoozedUntil = null,
        lastSignalScore = null,
        lastSignalLabel = null,
        lastSignalConfidence = null,
        lastSignalTime = null,
        notes = null,
        sortOrder = 0,
        tags = tags,
        muteMarketMovers = false,
        lastNotifiedAt = null,
        indicatorAlertsJson = indicatorAlertsJson
    )
}

/**
 * Factory for creating common test data scenarios
 */
object TestDataFactory {

    /**
     * Creates candles optimized for each ChartRange
     */
    fun candlesForRange(range: ChartRange, pattern: PriceCandleBuilder.PricePattern = PriceCandleBuilder.PricePattern.TRENDING_UP): List<PriceCandle> {
        return when (range) {
            ChartRange.ONE_DAY -> PriceCandleBuilder()
                .count(390) // 6.5 hours * 60 minutes
                .pattern(pattern)
                .volatility(0.005)
                .build()
            ChartRange.FIVE_DAY -> PriceCandleBuilder()
                .count(390 * 5)
                .pattern(pattern)
                .volatility(0.008)
                .build()
            ChartRange.ONE_MONTH -> PriceCandleBuilder()
                .count(390 * 21) // ~21 trading days
                .pattern(pattern)
                .volatility(0.01)
                .build()
            ChartRange.SIX_MONTH -> PriceCandleBuilder()
                .count(126) // ~126 trading days
                .pattern(pattern)
                .volatility(0.015)
                .build()
            ChartRange.ONE_YEAR -> PriceCandleBuilder()
                .count(252) // ~252 trading days
                .pattern(pattern)
                .volatility(0.02)
                .build()
            ChartRange.FIVE_YEAR -> PriceCandleBuilder()
                .count(252 * 5)
                .pattern(pattern)
                .volatility(0.025)
                .build()
        }
    }

    /**
     * Creates RSI scenarios for testing
     */
    fun rsiOversoldCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(20)
            .pattern(PriceCandleBuilder.PricePattern.STRONG_DOWNTREND)
            .basePrice(100.0)
            .build()
    }

    fun rsiOverboughtCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(20)
            .pattern(PriceCandleBuilder.PricePattern.STRONG_UPTREND)
            .basePrice(100.0)
            .build()
    }

    fun rsiNeutralCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(20)
            .pattern(PriceCandleBuilder.PricePattern.SIDEWAYS)
            .basePrice(100.0)
            .build()
    }

    /**
     * Creates MACD crossing scenarios
     */
    fun macdBullishCrossCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(50)
            .pattern(PriceCandleBuilder.PricePattern.REVERSAL_UP)
            .basePrice(100.0)
            .build()
    }

    fun macdBearishCrossCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(50)
            .pattern(PriceCandleBuilder.PricePattern.REVERSAL_DOWN)
            .basePrice(100.0)
            .build()
    }

    /**
     * Creates Bollinger Band scenarios
     */
    fun bollingerUpperBreakoutCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(30)
            .pattern(PriceCandleBuilder.PricePattern.STRONG_UPTREND)
            .volatility(0.01)
            .build()
    }

    fun bollingerLowerBreakdownCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(30)
            .pattern(PriceCandleBuilder.PricePattern.STRONG_DOWNTREND)
            .volatility(0.01)
            .build()
    }

    /**
     * Creates high/low volatility scenarios
     */
    fun lowVolatilityCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(20)
            .pattern(PriceCandleBuilder.PricePattern.FLAT)
            .volatility(0.001)
            .build()
    }

    fun highVolatilityCandles(): List<PriceCandle> {
        return PriceCandleBuilder()
            .count(20)
            .pattern(PriceCandleBuilder.PricePattern.VOLATILE)
            .volatility(0.08)
            .build()
    }

    /**
     * Creates flat/constant scenarios for edge case testing
     */
    fun flatPriceCandles(count: Int = 20, price: Double = 100.0): List<PriceCandle> {
        val now = LocalDateTime.of(2024, 1, 1, 9, 30)
        return List(count) { index ->
            PriceCandle(
                time = now.plusMinutes(index.toLong()),
                open = price,
                high = price,
                low = price,
                close = price,
                volume = 1_000_000L
            )
        }
    }

    /**
     * Creates candles with specific close prices for exact indicator testing
     */
    fun candlesWithCloses(closes: List<Double>, startTime: LocalDateTime = LocalDateTime.of(2024, 1, 1, 9, 30)): List<PriceCandle> {
        return closes.mapIndexed { index, close ->
            PriceCandle(
                time = startTime.plusMinutes(index.toLong()),
                open = close - 0.05,
                high = close + 0.15,
                low = close - 0.15,
                close = close,
                volume = 1_000_000L
            )
        }
    }
}
