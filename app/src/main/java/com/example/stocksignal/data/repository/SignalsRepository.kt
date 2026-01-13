package com.example.stocksignal.data.repository

import android.util.Log
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import com.example.stocksignal.data.local.repository.SignalEventsRepository
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.domain.signal.SignalEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalsRepository @Inject constructor(
    private val signalEventsRepository: SignalEventsRepository
) {

    val eventsFlow: Flow<List<NotificationEvent>> = signalEventsRepository.eventsFlow.map { events ->
        events.map { it.toDomain() }
    }

    fun eventsForTicker(ticker: String): Flow<List<NotificationEvent>> {
        return signalEventsRepository.eventsForTicker(ticker).map { events ->
            events.map { it.toDomain() }
        }
    }

    suspend fun recordEvent(event: NotificationEvent) {
        signalEventsRepository.upsert(event.toEntity())
    }

    suspend fun recordIndicatorEvent(event: NotificationEvent, label: String) {
        signalEventsRepository.upsert(event.toEntity(label))
    }

    suspend fun eventsByIds(ids: List<String>): List<NotificationEvent> {
        return signalEventsRepository.getByIds(ids).map { it.toDomain() }
    }

    suspend fun markNotified(ids: List<String>, notifiedAt: LocalDateTime) {
        signalEventsRepository.updateDelivery(ids, notifiedAt, true)
    }

    suspend fun isInCooldown(ticker: String, label: String, generatedAt: LocalDateTime): Boolean {
        val latest = signalEventsRepository.getLatestForTickerAndLabel(ticker, label) ?: return false
        val age = Duration.between(latest.generatedAt, generatedAt)
        return age < COOLDOWN
    }

    fun computeSignal(
        candles: List<PriceCandle>,
        range: ChartRange
    ): SignalResult? {
        return SignalEngine.computeSignal(candles, range)
    }

    suspend fun evaluateAndStoreSignal(
        ticker: String,
        candles: List<PriceCandle>,
        range: ChartRange,
        type: NotificationEventType = NotificationEventType.WATCHLIST_SIGNAL
    ): SignalResult? {
        try {
            val result = SignalEngine.computeSignal(candles, range) ?: return null
            val label = result.tier.label
            val latest = signalEventsRepository.getLatestForTickerAndLabel(ticker, label)
            if (latest != null) {
                val age = Duration.between(latest.generatedAt, result.generatedAt)
                if (age < COOLDOWN) {
                    Log.d(TAG, "Signal for $ticker/$label in cooldown (age: ${age.toMinutes()}m)")
                    return result
                }
            }
            signalEventsRepository.upsert(result.toEntity(ticker, type))
            Log.d(TAG, "Stored signal for $ticker: ${result.tier.label} (score: ${result.score})")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating/storing signal for $ticker", e)
            return null
        }
    }

    private fun GlobalSignalEventEntity.toDomain(): NotificationEvent {
        return NotificationEvent(
            id = id,
            type = parseType(type),
            ticker = ticker,
            companyName = null,
            score = score,
            averageScore = avgScore,
            modeScore = modeScore,
            confidence = confidence,
            price = price,
            percentChange = percentChange,
            generatedAt = generatedAt,
            notifiedAt = notifiedAt,
            deepLink = deepLink,
            source = source,
            delivered = delivered,
            reasons = reasons.mapIndexed { index, reason ->
                SignalReason(
                    id = "reason_$index",
                    title = reason,
                    explanation = reason,
                    impactScore = 0,
                    model = null
                )
            }
        )
    }

    private fun NotificationEvent.toEntity(): GlobalSignalEventEntity {
        val tier = SignalTier.fromScore(score)
        return GlobalSignalEventEntity(
            id = id,
            type = type.name.lowercase(),
            ticker = ticker,
            score = score,
            label = tier.label,
            confidence = confidence,
            percentChange = percentChange,
            price = price,
            generatedAt = generatedAt,
            notifiedAt = notifiedAt,
            source = source,
            delivered = delivered,
            deepLink = deepLink,
            reasons = reasons.map { it.title },
            avgScore = averageScore,
            modeScore = modeScore,
            modelScores = null
        )
    }

    private fun NotificationEvent.toEntity(labelOverride: String): GlobalSignalEventEntity {
        return GlobalSignalEventEntity(
            id = id,
            type = type.name.lowercase(),
            ticker = ticker,
            score = score,
            label = labelOverride,
            confidence = confidence,
            percentChange = percentChange,
            price = price,
            generatedAt = generatedAt,
            notifiedAt = notifiedAt,
            source = source,
            delivered = delivered,
            deepLink = deepLink,
            reasons = reasons.map { it.title },
            avgScore = averageScore,
            modeScore = modeScore,
            modelScores = null
        )
    }

    private fun SignalResult.toEntity(
        ticker: String,
        type: NotificationEventType
    ): GlobalSignalEventEntity {
        return GlobalSignalEventEntity(
            id = "sig_${ticker}_${generatedAt.toString().replace(':', '_')}",
            type = type.name.lowercase(),
            ticker = ticker,
            score = score,
            label = tier.label,
            confidence = confidence,
            percentChange = null,
            price = null,
            generatedAt = generatedAt,
            notifiedAt = null,
            source = "local",
            delivered = false,
            deepLink = "stocksignal://stock/$ticker",
            reasons = reasons.map { it.title },
            avgScore = averageScore,
            modeScore = modeScore,
            modelScores = modelScores
        )
    }

    private fun parseType(raw: String): NotificationEventType {
        val normalized = raw.replace('-', '_').replace(' ', '_').uppercase()
        return when (normalized) {
            "MARKET_MOVER" -> NotificationEventType.MARKET_MOVER
            "WATCHLIST_SIGNAL" -> NotificationEventType.WATCHLIST_SIGNAL
            "DIGEST" -> NotificationEventType.DIGEST
            else -> NotificationEventType.DIGEST
        }
    }

    companion object {
        private const val TAG = "SignalsRepository"
        private val COOLDOWN = Duration.ofHours(24)
    }
}
