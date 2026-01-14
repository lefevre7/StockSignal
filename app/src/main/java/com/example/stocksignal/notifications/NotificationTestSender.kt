package com.example.stocksignal.notifications

import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.SignalReason
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationTestSender @Inject constructor(
    private val publisher: NotificationPublisher,
    private val signalsRepository: SignalsRepository
) {

    suspend fun sendTestNotification(): Int {
        val now = LocalDateTime.now()
        val event = NotificationEvent(
            id = "test_${System.currentTimeMillis()}",
            type = NotificationEventType.WATCHLIST_SIGNAL,
            ticker = "TEST",
            companyName = "Test Signal",
            score = 80,
            averageScore = 80,
            modeScore = 80,
            confidence = 85,
            price = 123.45,
            percentChange = 2.34,
            generatedAt = now,
            notifiedAt = null,
            deepLink = "stocksignal://stock/TEST",
            source = "test",
            delivered = false,
            reasons = listOf(
                SignalReason(
                    id = "test_reason",
                    title = "Test alert generated on-device.",
                    explanation = "This is a local test notification to verify sound, vibration, and delivery.",
                    impactScore = 0,
                    model = "test"
                )
            )
        )

        signalsRepository.recordEvent(event)
        val notificationId = publisher.postDigest(listOf(event))
        if (notificationId != 0) {
            signalsRepository.markNotified(listOf(event.id), now)
        }
        return notificationId
    }
}
