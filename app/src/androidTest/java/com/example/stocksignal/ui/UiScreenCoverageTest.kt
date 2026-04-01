package com.example.stocksignal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.ui.marketmovers.MarketMoversScreen
import com.example.stocksignal.ui.marketmovers.MarketMoversUiState
import com.example.stocksignal.ui.notes.NotesScreen
import com.example.stocksignal.ui.notes.NotesUiState
import com.example.stocksignal.ui.search.SearchScreen
import com.example.stocksignal.ui.search.SearchUiState
import com.example.stocksignal.ui.search.WatchlistSummary
import com.example.stocksignal.ui.settings.SettingsScreen
import com.example.stocksignal.ui.signals.SignalsFeedScreen
import com.example.stocksignal.ui.signals.SignalsFeedUiState
import com.example.stocksignal.ui.stockdetail.StockDetailScreen
import com.example.stocksignal.ui.stockdetail.StockDetailTab
import com.example.stocksignal.ui.watchlist.WatchlistScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiScreenCoverageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreen_rendersSectionsAndInvokesActions() {
        var sendTestCount = 0
        var checkStatusCount = 0
        var forceScheduleCount = 0
        var downloadModelCount = 0

        composeRule.setStockSignalContent {
            SettingsScreen(
                settings = sampleAppSettings(),
                errorMessage = "alarm diagnostics ready",
                stooqBlockedMessage = "Requests paused until tomorrow at 07:24.",
                onClearError = {},
                onClearStooqBlocked = {},
                onRefreshStooqBlocked = {},
                onHoldingPeriodChange = {},
                onFrequencyChange = {},
                onNotificationTypeToggle = { _, _ -> },
                onQuietHoursToggle = {},
                onQuietHoursChange = { _, _ -> },
                onScheduleWindowChange = {},
                onWeeklyDayChange = {},
                onSnoozeDurationChange = {},
                onSignalSensitivityChange = {},
                onImmediatePostsToggle = {},
                onOfflineTranslationToggle = {},
                onDeleteOfflineTranslationModel = {},
                onDownloadModel = { downloadModelCount++ },
                getModelInfo = { "Gemma 3 1B int4" to "584.0 MB" },
                isModelDownloaded = true,
                modelDownloadProgress = 42,
                isDownloadingModel = true,
                onSendTestNotification = { sendTestCount++ },
                onCheckWorkerStatus = { checkStatusCount++ },
                onForceScheduleWorkers = { forceScheduleCount++ }
            )
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Downloading: 42%"))
        assertTrue(composeRule.onAllNodesWithText("Downloading: 42%").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_send_test_notification"))
        composeRule.onNodeWithTag("settings_send_test_notification", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_check_status"))
        composeRule.onNodeWithTag("settings_check_status", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("settings_force_schedule", useUnmergedTree = true).performClick()

        assertEquals(1, sendTestCount)
        assertEquals(1, checkStatusCount)
        assertEquals(1, forceScheduleCount)
        assertEquals(0, downloadModelCount)
    }

    @Test
    fun settingsScreen_rendersWeeklyConfigurationBranch() {
        composeRule.setStockSignalContent {
            SettingsScreen(
                settings = sampleAppSettings(
                    frequency = NotificationFrequency.ONE_PER_WEEK,
                    notificationTypes = setOf(NotificationType.WATCHLIST)
                ),
                onHoldingPeriodChange = {},
                onFrequencyChange = {},
                onNotificationTypeToggle = { _, _ -> },
                onQuietHoursToggle = {},
                onQuietHoursChange = { _, _ -> },
                onScheduleWindowChange = {},
                onWeeklyDayChange = {},
                onSnoozeDurationChange = {},
                onSignalSensitivityChange = {},
                onImmediatePostsToggle = {},
                onOfflineTranslationToggle = {},
                onDeleteOfflineTranslationModel = {},
                onDownloadModel = {},
                getModelInfo = { "Gemma 3 1B int4" to "Not downloaded" },
                isModelDownloaded = false,
                modelDownloadProgress = null,
                isDownloadingModel = false,
                onSendTestNotification = {},
                onCheckWorkerStatus = {},
                onForceScheduleWorkers = {}
            )
        }

        composeRule.onNodeWithTag("settings_list").performScrollToIndex(15)
        assertTrue(composeRule.onAllNodesWithText("Weekly day").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun watchlistScreen_rendersCardsAndGroupsByTag() {
        var removedSymbol: String? = null
        var snoozedSymbol: String? = null

        composeRule.setStockSignalContent {
            WatchlistScreen(
                items = listOf(
                    sampleWatchlistCardState(symbol = "AAPL"),
                    sampleWatchlistCardState(symbol = "MSFT", aiGenerationState = com.example.stocksignal.ui.model.AiGenerationState.QUEUED)
                ),
                errorMessage = "Market data degraded",
                stooqBlockedMessage = "Requests paused.",
                onClearError = {},
                onClearStooqBlock = {},
                onReorder = {},
                onRemove = { removedSymbol = it },
                onSnooze = { snoozedSymbol = it },
                onSearchClick = {},
                onOpenDetail = { _, _ -> },
                onAddNote = {},
                onOpenAlert = {}
            )
        }

        composeRule.onNodeWithText("Your Signals").assertIsDisplayed()
        composeRule.onNodeWithText("Stooq blocked: Requests paused.").assertIsDisplayed()
        composeRule.onNodeWithText("Market data degraded").assertIsDisplayed()
        composeRule.onNodeWithText("AAPL").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Add note").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("watchlist_remove_AAPL", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("watchlist_snooze_AAPL", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        assertTrue(composeRule.onAllNodesWithText("Tech").fetchSemanticsNodes().isNotEmpty())

        assertEquals("AAPL", removedSymbol)
        assertEquals("AAPL", snoozedSymbol)
    }

    @Test
    fun searchScreen_coversSuggestionsAndResultsBranches() {
        var openedMovers = false
        var addedMover = false
        var openedDetail: String? = null
        var addedResult = false
        var alertEnabled: Boolean? = null
        var state by mutableStateOf(
            SearchUiState(
                query = "",
                recentSearches = listOf(sampleRecentSearch("AAPL"), sampleRecentSearch("NVDA")),
                topMostActive = listOf(sampleMarketMoverItem()),
                moverSymbols = setOf("AAPL"),
                watchlist = mapOf("AAPL" to WatchlistSummary(symbol = "AAPL", alertEnabled = true))
            )
        )

        composeRule.setStockSignalContent {
            SearchScreen(
                state = state,
                onQueryChange = {},
                onClearQuery = {},
                onAddToWatchlist = { _, _ ->
                    if (state.query.isBlank()) {
                        addedMover = true
                    } else {
                        addedResult = true
                    }
                },
                onToggleAlert = { _, enabled -> alertEnabled = enabled },
                onSelectRecentSearch = {},
                onClearHistory = {},
                onSelectQuickFilter = {},
                onBack = {},
                onOpenMovers = { openedMovers = true },
                onOpenDetail = { openedDetail = it }
            )
        }

        composeRule.onNodeWithText("Search").assertIsDisplayed()
        composeRule.onAllNodesWithText("Most Active").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Recent searches").assertIsDisplayed()
        composeRule.onNodeWithText("Open Market Movers").performClick()
        composeRule.onNodeWithText("Details").performClick()

        assertTrue(openedMovers)
        assertEquals("NVDA", openedDetail)

        composeRule.runOnUiThread {
            state = SearchUiState(
                query = "apple",
                results = listOf(sampleSearchResult()),
                moverSymbols = setOf("AAPL"),
                watchlist = emptyMap(),
                isLoading = false
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AAPL").assertIsDisplayed()
        composeRule.onNodeWithText("Alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("Details").performClick()

        assertTrue(addedResult)
        assertEquals("AAPL", openedDetail)

        composeRule.runOnUiThread {
            state = SearchUiState(
                query = "apple",
                results = listOf(sampleSearchResult()),
                moverSymbols = setOf("AAPL"),
                watchlist = mapOf("AAPL" to WatchlistSummary(symbol = "AAPL", alertEnabled = true)),
                isLoading = false
            )
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()
        assertEquals(false, alertEnabled)
    }

    @Test
    fun marketMoversScreen_coversLoadingErrorAndLoadedBranches() {
        var refreshed = false
        var selectedDirection: MarketMoverDirection? = null
        var addedTicker: String? = null
        var alertTicker: String? = null
        var detailTicker: String? = null
        var state by mutableStateOf(MarketMoversUiState(isLoading = true))

        composeRule.setStockSignalContent {
            MarketMoversScreen(
                state = state,
                onDirectionSelected = { selectedDirection = it },
                onRefresh = { refreshed = true },
                onOpenDetail = { detailTicker = it },
                onOpenAlert = { alertTicker = it },
                onAddToWatchlist = { addedTicker = it.ticker }
            )
        }
        composeRule.onNodeWithText("Loading movers…").assertIsDisplayed()

        composeRule.runOnUiThread {
            state = MarketMoversUiState(
                direction = MarketMoverDirection.MOST_ACTIVE,
                items = listOf(
                    sampleMarketMoverItem(series = emptyList()),
                    sampleMarketMoverItem(ticker = "AMD", rank = 2, percentChange = -1.2)
                ),
                watchlistSymbols = setOf("AMD"),
                isLoading = false
            )
        }

        composeRule.onNodeWithText("Market Movers").assertIsDisplayed()
        composeRule.onNodeWithText("Decliners").performClick()
        composeRule.onAllNodesWithText("Add").onFirst().performClick()
        composeRule.onAllNodesWithText("Alert").onFirst().performClick()
        composeRule.onAllNodesWithText("Details").onFirst().performClick()
        composeRule.onNodeWithContentDescription("Refresh").performClick()

        assertEquals(MarketMoverDirection.DECREASERS, selectedDirection)
        assertEquals("NVDA", addedTicker)
        assertEquals("NVDA", alertTicker)
        assertEquals("NVDA", detailTicker)
        assertTrue(refreshed)
    }

    @Test
    fun signalsFeedScreen_filtersAndDismissesEvents() {
        var openedTicker: String? = null
        var dismissedEvent: String? = null
        var undoEvent: String? = null

        composeRule.setStockSignalContent {
            SignalsFeedScreen(
                state = SignalsFeedUiState(
                    events = listOf(
                        sampleNotificationEvent(id = "evt-watch", ticker = "AAPL", type = com.example.stocksignal.domain.model.NotificationEventType.WATCHLIST_SIGNAL),
                        sampleNotificationEvent(id = "evt-mover", ticker = "NVDA", type = com.example.stocksignal.domain.model.NotificationEventType.MARKET_MOVER)
                    )
                ),
                onOpenDetail = { ticker, _ -> openedTicker = ticker },
                onDismissEvent = { dismissedEvent = it },
                onUndoDismiss = { undoEvent = it }
            )
        }

        composeRule.onNodeWithText("Signals").assertIsDisplayed()
        composeRule.onNodeWithText("Movers").performClick()
        composeRule.onNodeWithText("NVDA").assertIsDisplayed()
        composeRule.onNodeWithText("All").performClick()
        composeRule.onNodeWithTag("signal_card_evt-watch").performClick()
        composeRule.onNodeWithTag("signal_event_evt-watch").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Signal dismissed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Undo").performClick()

        assertEquals("AAPL", openedTicker)
        assertEquals("evt-watch", dismissedEvent)
        assertEquals("evt-watch", undoEvent)
    }

    @Test
    fun notesScreen_savesClearsAndDeletesNotes() {
        var savedSymbol: String? = null
        var savedContent: String? = null
        var deletedSymbol: String? = null

        composeRule.setStockSignalContent {
            NotesScreen(
                state = NotesUiState(
                    notes = listOf(
                        com.example.stocksignal.data.local.entity.NoteEntity(
                            symbol = "AAPL",
                            content = "Watch support near 180.",
                            updatedAt = java.time.LocalDateTime.of(2026, 3, 31, 9, 40)
                        )
                    ),
                    errorMessage = "Unable to sync notes"
                ),
                onSaveNote = { symbol, content ->
                    savedSymbol = symbol
                    savedContent = content
                },
                onDeleteNote = { deletedSymbol = it },
                onClearError = {},
                initialSymbol = "MSFT"
            )
        }

        composeRule.onNodeWithText("Notes").assertIsDisplayed()
        composeRule.onNodeWithText("Unable to sync notes").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput(" Build a starter position.")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Clear").performClick()

        assertEquals("MSFT", savedSymbol)
        assertTrue(savedContent!!.contains("starter position"))
        assertEquals("AAPL", deletedSymbol)
    }

    @Test
    fun stockDetailScreen_coversTabsAlertSheetAndTranslationPrompt() {
        var selectedRange: String? = null
        var addNoteTicker: String? = null
        var shareTicker: String? = null
        var alertLoads = 0
        var alertSaves = 0
        var retryDownloads = 0
        var dismissPromptCount = 0
        var state by mutableStateOf(sampleStockDetailUiState(openAlerts = true, showTranslationPrompt = true))
        var selectedTab by mutableStateOf(StockDetailTab.OVERVIEW)

        composeRule.setStockSignalContent {
            StockDetailScreen(
                state = state,
                onBack = {},
                onSelectRange = {
                    selectedRange = it.label
                    state = state.copy(range = it)
                },
                onToggleWatchlist = {},
                onRefresh = {},
                onLoadIndicatorAlerts = { alertLoads++ },
                onUpdateIndicatorAlert = { _, _, _, _ -> },
                onSaveIndicatorAlerts = { alertSaves++ },
                onAddTag = {},
                onRemoveTag = {},
                onAddNote = { addNoteTicker = it },
                onShare = { ticker, _ -> shareTicker = ticker },
                onRetryTranslationDownload = { retryDownloads++ },
                onDownloadOfflineModel = {},
                onDismissTranslationPrompt = {
                    dismissPromptCount++
                    state = state.copy(
                        showTranslationPrompt = false,
                        translationDownloadInProgress = false
                    )
                },
                onExportData = {},
                selectedTab = selectedTab,
                onSelectedTabChange = { selectedTab = it }
            )
        }

        composeRule.onNodeWithText("AAPL").assertIsDisplayed()
        composeRule.onNodeWithText("Indicator alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Download translation and scoring model").assertIsDisplayed()
        composeRule.onNodeWithText("Hide").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Download translation and scoring model").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("stock_range_5d").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { selectedRange == "5D" }
        composeRule.runOnUiThread { selectedTab = StockDetailTab.METRICS }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("stock_content_metrics", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnUiThread { selectedTab = StockDetailTab.NEWS }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Apple launches a new device line").fetchSemanticsNodes().isNotEmpty())
        composeRule.runOnUiThread { selectedTab = StockDetailTab.SIGNALS }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("AI reasoning").fetchSemanticsNodes().isNotEmpty())
        composeRule.runOnUiThread { selectedTab = StockDetailTab.HISTORY }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("stock_cta_add_note", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("stock_cta_share", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.onNodeWithTag("stock_cta_set_alert", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(2, alertLoads)
        assertEquals(1, alertSaves)
        assertEquals(0, retryDownloads)
        assertEquals(1, dismissPromptCount)
        assertEquals("5D", selectedRange)
        assertEquals("AAPL", addNoteTicker)
        assertEquals("AAPL", shareTicker)
    }

    private fun ComposeContentTestRule.setStockSignalContent(content: @Composable () -> Unit) {
        setContent {
            com.example.stocksignal.ui.theme.StockSignalTheme {
                content()
            }
        }
    }

}
