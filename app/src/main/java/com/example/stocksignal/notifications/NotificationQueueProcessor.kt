package com.example.stocksignal.notifications

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.stocksignal.data.local.entity.NotificationStateEntity
import com.example.stocksignal.data.local.repository.NotificationStateRepository
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationQueueProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationStateRepository: NotificationStateRepository,
    private val signalsRepository: SignalsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val publisher: NotificationPublisher
) {

    suspend fun processCandidates(
        candidates: List<NotificationEvent>,
        settings: AppSettings
    ) {
        if (candidates.isEmpty()) {
            logD("No candidates to process.")
            return
        }

        val now = LocalDateTime.now()
        var state = normalizeState(notificationStateRepository.getState(), now, settings.frequency)
        logD(
            "Processing ${candidates.size} candidate(s); queued=${state.queuedEventIds.size} " +
                "active=${state.lastActiveNotificationId} dismissed=${state.dismissed}"
        )

        val (eligibleCandidates, blockedCandidates) = partitionByQuietHours(candidates, settings, now)
        if (state.lastActiveNotificationId != null && !state.dismissed) {
            logD("Active notification ${state.lastActiveNotificationId} still visible; queueing ${candidates.size} event(s).")
            state = queueEvents(state, candidates)
            notificationStateRepository.upsert(state)
            return
        }

        val cap = capFor(settings.frequency)
        val key = countKey(now, settings.frequency)
        val count = state.notificationCounts[key] ?: 0
        if (count >= cap) {
            logD("Notification cap reached ($count/$cap) for key $key; queueing ${candidates.size} event(s).")
            state = queueEvents(state, candidates)
            notificationStateRepository.upsert(state)
            return
        }

        val queued = loadQueuedEvents(state)
        val (eligibleQueued, blockedQueued) = partitionByQuietHours(queued, settings, now)
        val toPost = (eligibleQueued + eligibleCandidates).distinctBy { it.id }
        if (toPost.isEmpty()) {
            logD("No eligible events to post; queueing ${blockedCandidates.size} blocked candidate(s).")
            state = queueEvents(state, blockedCandidates)
            notificationStateRepository.upsert(state)
            return
        }

        toPost.forEach { event ->
            logD("Posting ${event.ticker} ${event.tier.label} score=${event.score} type=${event.type}")
        }
        val notificationId = publisher.postDigest(toPost)
        if (notificationId == 0) {
            logW("postDigest returned 0; aborting delivery for ${toPost.size} event(s).")
            return
        }

        signalsRepository.markNotified(toPost.map { it.id }, now)
        val updatedCounts = state.notificationCounts.toMutableMap()
        updatedCounts[key] = count + 1
        val remainingQueued = (blockedQueued + blockedCandidates).distinctBy { it.id }.map { it.id }
        notificationStateRepository.upsert(
            state.copy(
                lastActiveNotificationId = notificationId,
                lastActiveAt = now,
                dismissed = false,
                queuedEventIds = remainingQueued,
                notificationCounts = updatedCounts,
                lastResetAt = now
            )
        )
    }

    suspend fun processQueued(settings: AppSettings) {
        val now = LocalDateTime.now()
        var state = normalizeState(notificationStateRepository.getState(), now, settings.frequency)
        if (state.lastActiveNotificationId != null && !state.dismissed) return
        if (state.queuedEventIds.isEmpty()) return

        val cap = capFor(settings.frequency)
        val key = countKey(now, settings.frequency)
        val count = state.notificationCounts[key] ?: 0
        if (count >= cap) return

        val queued = loadQueuedEvents(state)
        val (eligible, blocked) = partitionByQuietHours(queued, settings, now)
        if (eligible.isEmpty()) return

        eligible.forEach { event ->
            logD("Posting queued ${event.ticker} ${event.tier.label} score=${event.score} type=${event.type}")
        }
        val notificationId = publisher.postDigest(eligible)
        if (notificationId == 0) {
            logW("postDigest returned 0 for queued events; aborting delivery for ${eligible.size} event(s).")
            return
        }

        signalsRepository.markNotified(eligible.map { it.id }, now)
        val updatedCounts = state.notificationCounts.toMutableMap()
        updatedCounts[key] = count + 1
        notificationStateRepository.upsert(
            state.copy(
                lastActiveNotificationId = notificationId,
                lastActiveAt = now,
                dismissed = false,
                queuedEventIds = blocked.map { it.id },
                notificationCounts = updatedCounts,
                lastResetAt = now
            )
        )
    }

    suspend fun reconcileState(settings: AppSettings) {
        val now = LocalDateTime.now()
        val normalized = normalizeState(notificationStateRepository.getState(), now, settings.frequency)
        notificationStateRepository.upsert(normalized)
        logD("Reconciled notification state for frequency ${settings.frequency}.")
    }

    private suspend fun loadQueuedEvents(state: NotificationStateEntity): List<NotificationEvent> {
        if (state.queuedEventIds.isEmpty()) return emptyList()
        val events = signalsRepository.eventsByIds(state.queuedEventIds)
        if (events.isEmpty()) return emptyList()
        val byId = events.associateBy { it.id }
        return state.queuedEventIds.mapNotNull { byId[it] }
    }

    private fun queueEvents(state: NotificationStateEntity, events: List<NotificationEvent>): NotificationStateEntity {
        val existing = state.queuedEventIds.toMutableSet()
        events.forEach { existing.add(it.id) }
        return state.copy(queuedEventIds = existing.toList())
    }

    private suspend fun partitionByQuietHours(
        events: List<NotificationEvent>,
        settings: AppSettings,
        now: LocalDateTime
    ): Pair<List<NotificationEvent>, List<NotificationEvent>> {
        if (events.isEmpty()) return emptyList<NotificationEvent>() to emptyList()
        val eligible = mutableListOf<NotificationEvent>()
        val blocked = mutableListOf<NotificationEvent>()
        for (event in events) {
            if (isAllowedToNotify(event, settings, now)) {
                eligible.add(event)
            } else {
                blocked.add(event)
                logD("Quiet hours block for ${event.ticker} ${event.tier.label} (${event.id})")
            }
        }
        logD("Quiet hours split: eligible=${eligible.size} blocked=${blocked.size}")
        return eligible to blocked
    }

    private suspend fun isAllowedToNotify(
        event: NotificationEvent,
        settings: AppSettings,
        now: LocalDateTime
    ): Boolean {
        val quietHours = if (event.type == NotificationEventType.WATCHLIST_SIGNAL) {
            val entry = watchlistRepository.getBySymbol(event.ticker)
            val start = entry?.quietHoursStart
            val end = entry?.quietHoursEnd
            if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                QuietHours(enabled = true, start = start, end = end)
            } else {
                settings.quietHours
            }
        } else {
            settings.quietHours
        }
        return !isInQuietHours(quietHours, now.toLocalTime())
    }

    private fun normalizeState(
        state: NotificationStateEntity?,
        now: LocalDateTime,
        frequency: NotificationFrequency
    ): NotificationStateEntity {
        val initial = state ?: NotificationStateEntity(
            lastActiveNotificationId = null,
            lastActiveAt = null,
            dismissed = true,
            queuedEventIds = emptyList(),
            notificationCounts = emptyMap(),
            lastResetAt = now
        )

        var updated = initial
        if (updated.lastActiveAt != null) {
            val age = Duration.between(updated.lastActiveAt, now)
            if (age > Duration.ofHours(24)) {
                updated = updated.copy(lastActiveNotificationId = null, dismissed = true, lastActiveAt = null)
            }
        }

        if (updated.lastActiveNotificationId != null && !isNotificationActive(updated.lastActiveNotificationId)) {
            updated = updated.copy(lastActiveNotificationId = null, dismissed = true, lastActiveAt = null)
        }

        val key = countKey(now, frequency)
        val counts = updated.notificationCounts
        val lastKey = updated.lastResetAt?.let { countKey(it, frequency) }
        if (lastKey != key) {
            updated = updated.copy(
                notificationCounts = mapOf(key to (counts[key] ?: 0)),
                lastResetAt = now
            )
        }
        return updated
    }

    private fun isNotificationActive(notificationId: Int): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val active: Array<StatusBarNotification> = manager.activeNotifications
        return active.any { it.id == notificationId }
    }

    private fun isInQuietHours(quietHours: QuietHours, now: LocalTime): Boolean {
        if (!quietHours.enabled) return false
        val start = parseTime(quietHours.start) ?: return false
        val end = parseTime(quietHours.end) ?: return false
        return if (start <= end) {
            now >= start && now <= end
        } else {
            now >= start || now <= end
        }
    }

    private fun parseTime(raw: String): LocalTime? {
        return runCatching { LocalTime.parse(raw) }.getOrNull()
    }

    private fun countKey(time: LocalDateTime, frequency: NotificationFrequency): String {
        return when (frequency) {
            NotificationFrequency.ONE_PER_WEEK -> weekKey(time.toLocalDate())
            else -> time.toLocalDate().toString()
        }
    }

    private fun weekKey(date: LocalDate): String {
        val weekFields = WeekFields.of(Locale.getDefault())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "$year-W$week"
    }

    private fun capFor(frequency: NotificationFrequency): Int {
        return when (frequency) {
            NotificationFrequency.THREE_PER_DAY -> 3
            NotificationFrequency.ONE_PER_DAY -> 1
            NotificationFrequency.ONE_PER_WEEK -> 1
            NotificationFrequency.ONLY_WHEN_OPEN -> 0
            NotificationFrequency.DEV_FIVE_MINUTES -> 99 // No cap for dev testing
        }
    }

    private fun logD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logW(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    companion object {
        private const val TAG = "NotificationQueueProcessor"
    }
}
