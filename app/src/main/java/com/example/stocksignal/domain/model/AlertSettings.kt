package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class AlertSettings(
    val enabled: Boolean,
    val minScoreForNotify: Int,
    val quietHoursStart: String?,
    val quietHoursEnd: String?,
    val snoozedUntil: LocalDateTime?,
    val alwaysNotify: Boolean,
    val ignoreMarketMovers: Boolean
)
