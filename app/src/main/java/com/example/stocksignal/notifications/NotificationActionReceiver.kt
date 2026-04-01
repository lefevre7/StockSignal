package com.example.stocksignal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.NotificationStateRepository
import com.example.stocksignal.data.local.repository.WatchlistRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationStateRepository: NotificationStateRepository
    @Inject lateinit var watchlistRepository: WatchlistRepository

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            when (intent.action) {
                ACTION_DISMISS -> handleDismiss(context, intent)
                ACTION_ADD_WATCHLIST -> handleAddToWatchlist(context, intent)
            }
            pendingResult?.finish()
        }
    }

    private suspend fun handleDismiss(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        val state = notificationStateRepository.getState()
        if (state != null) {
            notificationStateRepository.upsert(
                state.copy(
                    lastActiveNotificationId = null,
                    lastActiveAt = null,
                    dismissed = true
                )
            )
        }
    }

    private suspend fun handleAddToWatchlist(context: Context, intent: Intent) {
        val ticker = intent.getStringExtra(EXTRA_TICKER).orEmpty()
        val companyName = intent.getStringExtra(EXTRA_COMPANY).orEmpty()
        if (ticker.isNotBlank()) {
            val existing = watchlistRepository.getBySymbol(ticker)
            if (existing == null) {
                val current = watchlistRepository.getAll()
                val maxSort = current.mapNotNull { it.sortOrder }.maxOrNull()
                val nextOrder = (maxSort ?: (current.size - 1)).coerceAtLeast(-1) + 1
                watchlistRepository.upsert(
                    WatchlistItemEntity(
                        symbol = ticker,
                        companyName = companyName.ifBlank { ticker },
                        exchange = null,
                        addedAt = LocalDateTime.now(),
                        alertEnabled = true,
                        minScoreForNotify = 60,
                        quietHoursStart = null,
                        quietHoursEnd = null,
                        snoozedUntil = null,
                        lastSignalScore = null,
                        lastSignalLabel = null,
                        lastSignalConfidence = null,
                        lastSignalTime = null,
                        notes = null,
                        sortOrder = nextOrder,
                        tags = emptyList(),
                        muteMarketMovers = false,
                        lastNotifiedAt = null,
                        indicatorAlertsJson = null
                    )
                )
            }
        }
        handleDismiss(context, intent)
    }

    companion object {
        const val ACTION_DISMISS = "com.example.stocksignal.action.DISMISS"
        const val ACTION_ADD_WATCHLIST = "com.example.stocksignal.action.ADD_WATCHLIST"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_EVENT_IDS = "extra_event_ids"
        const val EXTRA_TICKER = "extra_ticker"
        const val EXTRA_COMPANY = "extra_company"
    }
}
