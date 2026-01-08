package com.example.stocksignal.domain.model

import java.util.Locale

enum class AlertDirection {
    ABOVE,
    BELOW
}

enum class IndicatorMetric(
    val label: String,
    val defaultThreshold: Double,
    val defaultDirection: AlertDirection,
    val defaultRange: ChartRange
) {
    RSI_14("RSI (14)", 30.0, AlertDirection.BELOW, ChartRange.ONE_MONTH),
    MACD_HISTOGRAM("MACD Histogram", 0.0, AlertDirection.ABOVE, ChartRange.ONE_MONTH),
    MACD_LINE("MACD Line", 0.0, AlertDirection.ABOVE, ChartRange.ONE_MONTH),
    SMA_50_DISTANCE("SMA 50 Distance %", 0.0, AlertDirection.ABOVE, ChartRange.ONE_YEAR),
    SMA_200_DISTANCE("SMA 200 Distance %", 0.0, AlertDirection.ABOVE, ChartRange.ONE_YEAR),
    BOLLINGER_PERCENT_B("Bollinger %B", 80.0, AlertDirection.ABOVE, ChartRange.ONE_MONTH),
    ATR_PERCENT("ATR % (14)", 5.0, AlertDirection.ABOVE, ChartRange.ONE_MONTH),
    RETURN_ZSCORE_20("Return Z-Score (20)", 2.0, AlertDirection.ABOVE, ChartRange.ONE_MONTH)
}

data class IndicatorAlertSetting(
    val metric: IndicatorMetric,
    val threshold: Double,
    val direction: AlertDirection,
    val enabled: Boolean
)

object IndicatorAlertDefaults {

    fun defaultAlerts(): List<IndicatorAlertSetting> {
        return IndicatorMetric.values().map { metric ->
            IndicatorAlertSetting(
                metric = metric,
                threshold = metric.defaultThreshold,
                direction = metric.defaultDirection,
                enabled = false
            )
        }
    }

    fun defaultDescription(metric: IndicatorMetric): String {
        val direction = if (metric.defaultDirection == AlertDirection.ABOVE) "above" else "below"
        val threshold = formatValue(metric.defaultThreshold)
        return "Default: $direction $threshold (${metric.defaultRange.label})"
    }

    fun formatValue(value: Double): String {
        val formatted = String.format(Locale.US, "%.2f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }
}
