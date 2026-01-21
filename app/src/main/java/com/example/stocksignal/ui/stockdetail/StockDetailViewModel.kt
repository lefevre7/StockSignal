package com.example.stocksignal.ui.stockdetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.translation.ModelAvailability
import com.example.stocksignal.data.translation.NewsTranslationService
import com.example.stocksignal.domain.model.IndicatorAlertDefaults
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.StockNewsItem
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.TechnicalIndicators
import com.example.stocksignal.domain.signal.IndicatorCalculator
import com.example.stocksignal.domain.export.IntradayDataExporter
import com.example.stocksignal.domain.export.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val signalsRepository: SignalsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val settingsRepository: SettingsRepository,
    private val translationService: NewsTranslationService,
    private val intradayDataExporter: IntradayDataExporter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockDetailUiState())
    val uiState: StateFlow<StockDetailUiState> = _uiState.asStateFlow()

    private var watchlistSnapshot: List<WatchlistItemEntity> = emptyList()
    private var historyJob: Job? = null
    private var localModelDownloadJob: Job? = null
    private var pendingTranslation: PendingTranslation? = null
    private val promptedTranslationTickers = mutableSetOf<String>()

    init {
        observeWatchlist()
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            _uiState.update { it.copy(range = settings.selectedChartRange) }
            val tickerArg = savedStateHandle.get<String>(ARG_TICKER).orEmpty()
            val eventIdArg = savedStateHandle.get<String>(ARG_EVENT_ID)
            val openAlertsArg = savedStateHandle.get<Boolean>(ARG_OPEN_ALERTS) ?: false
            _uiState.update {
                it.copy(
                    highlightEventId = eventIdArg,
                    openAlerts = openAlertsArg
                )
            }
            if (tickerArg.isNotBlank()) {
                setTicker(tickerArg)
            }
        }
    }

    fun setTicker(ticker: String) {
        if (ticker.isBlank() || ticker == _uiState.value.ticker) return
        pendingTranslation = null
        _uiState.update {
            it.copy(
                ticker = ticker,
                companyName = null,
                exchange = null,
                errorMessage = null,
                tags = emptyList(),
                indicatorAlerts = emptyList(),
                marketCap = null,
                peRatio = null,
                dividend = null,
                week52High = null,
                week52Low = null,
                news = emptyList(),
                translationMessage = null,
                showTranslationPrompt = false,
                translationPromptTitle = null,
                translationPromptMessage = null,
                translationPromptType = null,
                translationDownloadProgress = null,
                translationDownloadInProgress = false,
                showTranslationRetry = false,
                offlineTranslationEnabled = false,
                localModelAvailable = false,
                localModelIncompatible = false,
                overviewError = null
            )
        }
        updateWatchlistMetadata()
        startHistoryObserver(ticker)
        loadSeries()
        loadOverview()
    }

    fun selectRange(range: ChartRange) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range) }
        viewModelScope.launch {
            settingsRepository.setSelectedChartRange(range)
        }
        loadSeries()
    }

    fun refresh() {
        loadSeries(forceRefresh = true)
        loadOverview(forceRefresh = true)
    }

    fun toggleWatchlist() {
        val state = _uiState.value
        if (state.ticker.isBlank()) return
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(state.ticker)
            if (existing != null) {
                watchlistRepository.deleteBySymbol(state.ticker)
                _uiState.update { it.copy(inWatchlist = false, alertEnabled = false, tags = emptyList()) }
            } else {
                val entity = buildWatchlistEntry(
                    tags = state.tags,
                    indicatorAlertsJson = null,
                    alertEnabled = true
                )
                watchlistRepository.upsert(entity)
            }
        }
    }

    fun loadIndicatorAlerts() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        viewModelScope.launch {
            val entry = watchlistRepository.getBySymbol(ticker)
            val stored = IndicatorAlertJson.fromJson(entry?.indicatorAlertsJson)
            val resolved = if (stored.isEmpty()) {
                IndicatorAlertDefaults.defaultAlerts()
            } else {
                stored
            }
            _uiState.update { it.copy(indicatorAlerts = resolved) }
        }
    }

    fun updateIndicatorAlert(
        metric: IndicatorMetric,
        enabled: Boolean? = null,
        threshold: Double? = null,
        direction: AlertDirection? = null
    ) {
        _uiState.update { state ->
            val base = if (state.indicatorAlerts.isEmpty()) {
                IndicatorAlertDefaults.defaultAlerts()
            } else {
                state.indicatorAlerts
            }
            val updated = base.map { alert ->
                if (alert.metric != metric) {
                    alert
                } else {
                    alert.copy(
                        enabled = enabled ?: alert.enabled,
                        threshold = threshold ?: alert.threshold,
                        direction = direction ?: alert.direction
                    )
                }
            }
            state.copy(indicatorAlerts = updated)
        }
    }

    fun saveIndicatorAlerts() {
        val state = _uiState.value
        val ticker = state.ticker
        if (ticker.isBlank()) return
        viewModelScope.launch {
            val alerts = state.indicatorAlerts
            val enabledAlerts = alerts.any { it.enabled }
            val encoded = IndicatorAlertJson.toJson(alerts)
            val existing = watchlistRepository.getBySymbol(ticker)
            if (existing == null) {
                val entity = buildWatchlistEntry(
                    tags = state.tags,
                    indicatorAlertsJson = encoded,
                    alertEnabled = enabledAlerts
                )
                watchlistRepository.upsert(entity)
                _uiState.update { it.copy(inWatchlist = true, alertEnabled = enabledAlerts) }
            } else {
                val updated = existing.copy(
                    alertEnabled = existing.alertEnabled || enabledAlerts,
                    indicatorAlertsJson = encoded
                )
                watchlistRepository.upsert(updated)
                _uiState.update { it.copy(alertEnabled = updated.alertEnabled, inWatchlist = true) }
            }
        }
    }

    fun addTag(rawTag: String) {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val tag = rawTag.trim()
        if (tag.isBlank()) return
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(ticker)
            val updatedTags = mergeTags(existing?.tags.orEmpty(), tag)
            if (existing == null) {
                val entity = buildWatchlistEntry(
                    tags = updatedTags,
                    indicatorAlertsJson = null,
                    alertEnabled = true
                )
                watchlistRepository.upsert(entity)
                _uiState.update { it.copy(inWatchlist = true, tags = updatedTags) }
            } else if (updatedTags != existing.tags) {
                watchlistRepository.upsert(existing.copy(tags = updatedTags))
                _uiState.update { it.copy(tags = updatedTags) }
            }
        }
    }

    fun removeTag(tag: String) {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(ticker) ?: return@launch
            val updatedTags = existing.tags.filterNot { it.equals(tag, ignoreCase = true) }
            if (updatedTags != existing.tags) {
                watchlistRepository.upsert(existing.copy(tags = updatedTags))
                _uiState.update { it.copy(tags = updatedTags) }
            }
        }
    }

    fun exportHistoricalData(outputFile: java.io.File) {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        viewModelScope.launch {
            when (val result = intradayDataExporter.exportToCSV(ticker, outputFile)) {
                is ExportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            exportMessage = "Exported ${result.rowCount} candles " +
                                "from ${result.dateRange.first} to ${result.dateRange.second} " +
                                "to ${outputFile.name}"
                        )
                    }
                }
                is ExportResult.NoData -> {
                    _uiState.update {
                        it.copy(exportMessage = "No historical data available for $ticker")
                    }
                }
                is ExportResult.Error -> {
                    _uiState.update {
                        it.copy(exportMessage = "Export failed: ${result.message}")
                    }
                }
            }
        }
    }

    private fun loadOverview(forceRefresh: Boolean = false) {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        viewModelScope.launch {
            when (val result = stockRepository.getStockOverview(ticker, forceRefresh)) {
                is Result.Success -> {
                    val overview = result.data
                    _uiState.update {
                        it.copy(
                            marketCap = overview.marketCap,
                            peRatio = overview.peRatio,
                            dividend = overview.dividend,
                            week52High = overview.week52High,
                            week52Low = overview.week52Low,
                            news = overview.news,
                            translationMessage = null,
                            showTranslationRetry = false,
                            overviewError = null
                        )
                    }
                    translateNewsIfNeeded(ticker, overview.news)
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            overviewError = result.message,
                            marketCap = null,
                            peRatio = null,
                            dividend = null,
                            week52High = null,
                            week52Low = null,
                            news = emptyList(),
                            translationMessage = null,
                            showTranslationRetry = false
                        )
                    }
                }
            }
        }
    }

    fun confirmTranslationDownload() {
        val pending = pendingTranslation ?: return
        if (pending.promptType != TranslationPromptType.PLAY_SERVICES) {
            Log.w(TAG, "confirmTranslationDownload called without Play services prompt.")
            retryTranslationDownload()
            return
        }
        _uiState.update {
            it.copy(
                translationPromptMessage = "Downloading Play services translation model.",
                translationDownloadInProgress = true,
                showTranslationPrompt = true
            )
        }
        viewModelScope.launch {
            if (!translationService.hasEnoughStorage(TRANSLATION_STORAGE_BYTES)) {
                Log.w(TAG, "Not enough storage to download translation model.")
                _uiState.update {
                    it.copy(
                        translationMessage = "Not enough storage for the translation model.",
                        translationDownloadInProgress = false,
                        showTranslationPrompt = false,
                        translationPromptTitle = null,
                        translationPromptMessage = null,
                        translationPromptType = null,
                        translationDownloadProgress = null,
                        showTranslationRetry = false
                    )
                }
                return@launch
            }
            val success = translationService.downloadModel()
            _uiState.update {
                it.copy(
                    translationDownloadInProgress = false,
                    showTranslationPrompt = false,
                    translationPromptTitle = null,
                    translationPromptMessage = null,
                    translationPromptType = null,
                    translationDownloadProgress = null
                )
            }
            if (!success) {
                Log.w(TAG, "Play services model download failed. Falling back to local model.")
                pendingTranslation = null
                showLocalModelPrompt(pending.ticker, pending.news, forcePrompt = true)
                return@launch
            }
            pendingTranslation = null
            translateNewsIfNeeded(pending.ticker, pending.news, allowPrompt = false)
        }
    }

    fun dismissTranslationPrompt() {
        val state = _uiState.value
        if (state.translationDownloadInProgress &&
            state.translationPromptType == TranslationPromptType.LOCAL_MODEL
        ) {
            _uiState.update { it.copy(showTranslationPrompt = false) }
            return
        }
        pendingTranslation = null
        _uiState.update {
            it.copy(
                showTranslationPrompt = false,
                translationPromptTitle = null,
                translationPromptMessage = null,
                translationPromptType = null,
                translationDownloadProgress = null,
                translationDownloadInProgress = false
            )
        }
    }

    fun requestOfflineModelDownload() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val news = _uiState.value.news
        if (news.isEmpty()) return
        viewModelScope.launch {
            try {
                settingsRepository.setOfflineTranslationEnabled(true)
                _uiState.update { it.copy(translationMessage = null, offlineTranslationEnabled = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable offline translation before download.", e)
                _uiState.update {
                    it.copy(
                        translationMessage = "Failed to enable offline translation.",
                        showTranslationRetry = true
                    )
                }
                return@launch
            }
            showLocalModelPrompt(ticker, news, forcePrompt = true)
        }
    }

    fun retryTranslationDownload() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val news = _uiState.value.news
        if (news.isEmpty()) return
        Log.d(TAG, "Manual retry for offline translation model download.")
        viewModelScope.launch {
            val currentState = _uiState.value
            val offlineEnabled = resolveOfflineTranslationEnabled()
            _uiState.update { it.copy(offlineTranslationEnabled = offlineEnabled) }
            if (!offlineEnabled) {
                Log.w(TAG, "Offline translation disabled; cannot download local model.")
                _uiState.update {
                    it.copy(
                        translationMessage = "Enable offline translation in Settings to download the model.",
                        showTranslationPrompt = false,
                        translationPromptTitle = null,
                        translationPromptMessage = null,
                        translationPromptType = null,
                        translationDownloadProgress = null,
                        translationDownloadInProgress = false,
                        showTranslationRetry = false
                    )
                }
                return@launch
            }
            if (currentState.showTranslationPrompt &&
                currentState.translationPromptType == TranslationPromptType.LOCAL_MODEL &&
                !currentState.translationDownloadInProgress
            ) {
                val pending = pendingTranslation
                    ?: PendingTranslation(ticker, news, TranslationPromptType.LOCAL_MODEL)
                pendingTranslation = pending
                beginLocalModelDownload(pending)
            } else {
                showLocalModelPrompt(ticker, news, forcePrompt = true)
            }
        }
    }

    private suspend fun showLocalModelPrompt(
        ticker: String,
        news: List<StockNewsItem>,
        forcePrompt: Boolean
    ) {
        val offlineEnabled = resolveOfflineTranslationEnabled()
        if (!offlineEnabled) {
            Log.w(TAG, "Offline translation disabled; skipping local model prompt.")
            if (!forcePrompt) return
            _uiState.update {
                it.copy(
                    translationMessage = "Enable offline translation in Settings to download the model.",
                    showTranslationPrompt = false,
                    translationPromptTitle = null,
                    translationPromptMessage = null,
                    translationPromptType = null,
                    translationDownloadProgress = null,
                    translationDownloadInProgress = false,
                    showTranslationRetry = false
                )
            }
            return
        }
        if (localModelDownloadJob?.isActive == true) {
            Log.d(TAG, "Local model download already in progress.")
            return
        }
        if (translationService.isLocalModelUsable()) {
            Log.d(TAG, "Local model already available; translating for $ticker.")
            translateNewsSerially(ticker, news, translationService::translateWithLocalModel, "local_model")
            return
        }
        if (!forcePrompt && !shouldPromptForTicker(ticker)) return
        if (forcePrompt) {
            promptedTranslationTickers.add(ticker)
        }
        pendingTranslation = PendingTranslation(
            ticker = ticker,
            news = news,
            promptType = TranslationPromptType.LOCAL_MODEL
        )
        Log.d(TAG, "Showing local model download prompt for $ticker.")
        _uiState.update {
            it.copy(
                showTranslationPrompt = true,
                translationPromptTitle = "Download 270M offline translation model",
                translationPromptMessage = "Download the 270M offline model to translate headlines? " +
                    "Wi-Fi required; uses ~304MB.",
                translationPromptType = TranslationPromptType.LOCAL_MODEL,
                translationDownloadProgress = null,
                translationDownloadInProgress = false,
                translationMessage = null,
                showTranslationRetry = false
            )
        }
    }

    private fun beginLocalModelDownload(pending: PendingTranslation) {
        if (localModelDownloadJob?.isActive == true) {
            Log.d(TAG, "Local model download already in progress.")
            return
        }
        localModelDownloadJob = viewModelScope.launch {
            if (translationService.isLocalModelUsable()) {
                Log.d(TAG, "Local model already available; translating for ${pending.ticker}.")
                _uiState.update {
                    it.copy(
                        showTranslationPrompt = false,
                        translationPromptTitle = null,
                        translationPromptMessage = null,
                        translationPromptType = null,
                        translationDownloadProgress = null,
                        translationDownloadInProgress = false
                    )
                }
                translateNewsSerially(
                    pending.ticker,
                    pending.news,
                    translationService::translateWithLocalModel,
                    "local_model"
                )
                return@launch
            }
            if (!translationService.isOnWifi()) {
                Log.w(TAG, "Wifi required for local model download.")
                _uiState.update {
                    it.copy(
                        translationMessage = "Wi-Fi required to download the 270M offline model.",
                        showTranslationPrompt = false,
                        translationPromptTitle = null,
                        translationPromptMessage = null,
                        translationPromptType = null,
                        translationDownloadProgress = null,
                        translationDownloadInProgress = false,
                        showTranslationRetry = true
                    )
                }
                return@launch
            }
            val requiredBytes = translationService.getLocalModelRequiredBytes()
            if (!translationService.hasEnoughStorage(requiredBytes)) {
                Log.w(TAG, "Not enough storage to download local model.")
                _uiState.update {
                    it.copy(
                        translationMessage = "Not enough storage for the 270M offline model.",
                        showTranslationPrompt = false,
                        translationPromptTitle = null,
                        translationPromptMessage = null,
                        translationPromptType = null,
                        translationDownloadProgress = null,
                        translationDownloadInProgress = false,
                        showTranslationRetry = true
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    showTranslationPrompt = true,
                    translationPromptTitle = "Download 270M offline translation model",
                    translationPromptMessage = "Downloading the 270M offline model for translation.",
                    translationPromptType = TranslationPromptType.LOCAL_MODEL,
                    translationDownloadProgress = 0,
                    translationDownloadInProgress = true,
                    translationMessage = null,
                    showTranslationRetry = false
                )
            }
            val success = translationService.downloadLocalModel { progress ->
                _uiState.update { state ->
                    state.copy(
                        translationDownloadProgress = progress,
                        translationDownloadInProgress = true
                    )
                }
            }
            if (!success) {
                Log.w(TAG, "Offline model download failed; attempting asset pack fallback.")
                val assetPackSuccess = translationService.tryFetchLocalModelFromAssetPack()
                if (!assetPackSuccess) {
                    val modelPath = translationService.getLocalModelFilePath()
                    val modelHash = translationService.getLocalModelSha256()
                    _uiState.update {
                        it.copy(
                            translationMessage = "Offline model download failed. " +
                                "Sideload to $modelPath (SHA-256: $modelHash) and retry.",
                            translationDownloadProgress = null,
                            translationDownloadInProgress = false,
                            showTranslationPrompt = false,
                            translationPromptTitle = null,
                            translationPromptMessage = null,
                            translationPromptType = null,
                            showTranslationRetry = true
                        )
                    }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    translationDownloadProgress = null,
                    translationDownloadInProgress = false,
                    showTranslationPrompt = false,
                    translationPromptTitle = null,
                    translationPromptMessage = null,
                    translationPromptType = null,
                    translationMessage = null,
                    showTranslationRetry = false
                )
            }
            pendingTranslation = null
            translateNewsIfNeeded(pending.ticker, pending.news, allowPrompt = false)
        }
    }

    private fun shouldPromptForTicker(ticker: String): Boolean {
        if (promptedTranslationTickers.contains(ticker)) {
            Log.d(TAG, "Translation prompt already shown for $ticker.")
            return false
        }
        promptedTranslationTickers.add(ticker)
        return true
    }

    private suspend fun translateNewsIfNeeded(
        ticker: String,
        news: List<StockNewsItem>,
        allowPrompt: Boolean = true
    ) {
        if (news.none { shouldTranslate(it) }) return
        val offlineEnabled = resolveOfflineTranslationEnabled()
        val localAvailable = translationService.isLocalModelAvailable()
        val localUsable = offlineEnabled && localAvailable && !translationService.isLocalModelIncompatible()
        _uiState.update {
            it.copy(
                offlineTranslationEnabled = offlineEnabled,
                localModelAvailable = localAvailable,
                localModelIncompatible = translationService.isLocalModelIncompatible()
            )
        }
        val availability = translationService.getModelAvailability()
        Log.d(
            TAG,
            "Translation availability for $ticker. " +
                "Play services=$availability, local=$localAvailable, offlineEnabled=$offlineEnabled"
        )
        when (availability) {
            ModelAvailability.AVAILABLE -> {
                translateNewsSerially(ticker, news, translationService::translateWithMlkit, "play_services")
            }
            ModelAvailability.NEEDS_DOWNLOAD -> {
                if (localUsable) {
                    Log.d(TAG, "Play services needs download; using local model for $ticker.")
                    translateNewsSerially(ticker, news, translationService::translateWithLocalModel, "local_model")
                    return
                }
                if (!allowPrompt || !shouldPromptForTicker(ticker)) return
                if (!translationService.hasEnoughStorage(TRANSLATION_STORAGE_BYTES)) {
                    Log.w(TAG, "Not enough storage to download Play services translation model.")
                    _uiState.update {
                        it.copy(
                            translationMessage = "Not enough storage for the translation model.",
                            showTranslationPrompt = false,
                            showTranslationRetry = false
                        )
                    }
                    return
                }
                pendingTranslation = PendingTranslation(
                    ticker = ticker,
                    news = news,
                    promptType = TranslationPromptType.PLAY_SERVICES
                )
                _uiState.update {
                    it.copy(
                        showTranslationPrompt = true,
                        translationPromptTitle = "Download translation model",
                        translationPromptMessage = "Download the Play services translation model to translate headlines.",
                        translationPromptType = TranslationPromptType.PLAY_SERVICES,
                        translationDownloadProgress = null,
                        translationDownloadInProgress = false,
                        showTranslationRetry = false
                    )
                }
            }
            ModelAvailability.UNAVAILABLE -> {
                Log.w(TAG, "Play services translation model unavailable on this device.")
                if (localUsable) {
                    Log.d(TAG, "Local model available; translating with local model for $ticker.")
                    translateNewsSerially(ticker, news, translationService::translateWithLocalModel, "local_model")
                    return
                }
                if (translationService.isLocalModelIncompatible()) {
                    Log.w(TAG, "Local model incompatible; falling back to cloud translation.")
                    val detailMessage = translationService.getLocalModelIncompatibilityMessage()
                        ?: "Offline model incompatible with this app version."
                    _uiState.update {
                        it.copy(
                            translationMessage = detailMessage,
                            showTranslationRetry = true,
                            localModelIncompatible = true
                        )
                    }
                    // Fallback to cloud MLKit translation
                    translateNewsSerially(ticker, news, translationService::translateWithMlkit, "mlkit_cloud")
                    return
                }
                if (!offlineEnabled) {
                    Log.w(TAG, "Offline translation disabled; cannot offer local model.")
                    if (allowPrompt) {
                        _uiState.update {
                            it.copy(
                                translationMessage = "Play services model unavailable. " +
                                    "Enable offline translation in Settings to download a model.",
                                showTranslationPrompt = false,
                                translationPromptTitle = null,
                                translationPromptMessage = null,
                                translationPromptType = null,
                                translationDownloadProgress = null,
                                translationDownloadInProgress = false,
                                showTranslationRetry = false
                            )
                        }
                    }
                    return
                }
                if (!allowPrompt) return
                showLocalModelPrompt(ticker, news, forcePrompt = false)
            }
        }
    }

    private suspend fun resolveOfflineTranslationEnabled(): Boolean {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.offlineTranslationEnabled) return true
        if (settingsRepository.isOfflineTranslationPreferenceSet()) return false
        val localUsable = translationService.isLocalModelUsable()
        if (!localUsable) return false
        return try {
            settingsRepository.setOfflineTranslationEnabled(true)
            Log.i(TAG, "Auto-enabled offline translation because a local model is present.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-enable offline translation", e)
            false
        }
    }

    private suspend fun translateNewsSerially(
        ticker: String,
        news: List<StockNewsItem>,
        translator: suspend (String) -> String?,
        providerLabel: String
    ) {
        val updated = news.toMutableList()
        Log.d(TAG, "Translating news for $ticker using $providerLabel.")
        if (_uiState.value.ticker == ticker) {
            _uiState.update { it.copy(translationMessage = null, showTranslationRetry = false) }
        }
        if (providerLabel == "local_model" && translationService.isLocalModelIncompatible()) {
            Log.w(TAG, "Local model already marked incompatible; falling back to cloud.")
            if (_uiState.value.ticker == ticker) {
                val detailMessage = translationService.getLocalModelIncompatibilityMessage()
                    ?: "Offline model incompatible."
                _uiState.update {
                    it.copy(
                        translationMessage = "$detailMessage Using cloud translation.",
                        showTranslationRetry = false,
                        localModelIncompatible = true
                    )
                }
            }
            // Fallback to cloud MLKit translation
            translateNewsSerially(ticker, news, translationService::translateWithMlkit, "mlkit_cloud")
            return
        }
        for (index in updated.indices) {
            val item = updated[index]
            if (!shouldTranslate(item)) continue
            val input = buildTranslationInput(item)
            if (input.isBlank()) continue
            val translated = translateWithRetry(input, translator, providerLabel)
            if (providerLabel == "local_model" && translationService.isLocalModelIncompatible()) {
                // Local model became incompatible mid-translation; fallback to cloud
                Log.w(TAG, "Local model became incompatible during translation; falling back to cloud.")
                if (_uiState.value.ticker == ticker) {
                    val detailMessage = translationService.getLocalModelIncompatibilityMessage()
                        ?: "Offline model incompatible."
                    _uiState.update {
                        it.copy(
                            translationMessage = "$detailMessage Using cloud translation.",
                            showTranslationRetry = false,
                            localModelIncompatible = true
                        )
                    }
                }
                // Continue with remaining items using cloud translation
                val remainingNews = updated.subList(index, updated.size).toList()
                translateNewsSerially(ticker, remainingNews, translationService::translateWithMlkit, "mlkit_cloud")
                return
            }
            if (translated == null) continue
            val updatedItem = item.copy(
                translatedTitle = translated,
                translatedPublishedAtText = null
            )
            updated[index] = updatedItem
            if (_uiState.value.ticker == ticker) {
                _uiState.update { it.copy(news = updated.toList()) }
                stockRepository.updateOverviewNews(ticker, updated.toList())
            }
        }
    }

    private suspend fun translateWithRetry(
        input: String,
        translator: suspend (String) -> String?,
        providerLabel: String
    ): String? {
        repeat(2) { attempt ->
            val translated = translator(input) ?: return@repeat
            if (isTranslationAcceptable(input, translated)) {
                return translated.trim()
            }
            if (attempt == 0) {
                Log.w(TAG, "Translation matched input via $providerLabel, retrying once.")
            }
        }
        Log.w(TAG, "Translation failed or unchanged after retry via $providerLabel.")
        return null
    }

    private fun shouldTranslate(item: StockNewsItem): Boolean {
        return item.translatedTitle.isNullOrBlank() && item.title.isNotBlank()
    }

    private fun buildTranslationInput(item: StockNewsItem): String {
        val title = item.title.trim()
        val date = item.publishedAtText.trim()
        val source = item.source?.trim().orEmpty()
        return when {
            date.isNotBlank() && source.isNotBlank() -> "$title. $date * $source"
            date.isNotBlank() -> "$title. $date"
            source.isNotBlank() -> "$title. $source"
            else -> title
        }
    }

    private fun isTranslationAcceptable(input: String, translated: String): Boolean {
        val cleaned = translated.trim()
        return cleaned.isNotEmpty() && cleaned != input.trim()
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistRepository.watchlistFlow.collectLatest { items ->
                watchlistSnapshot = items
                updateWatchlistMetadata()
            }
        }
    }

    private fun updateWatchlistMetadata() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val entry = watchlistSnapshot.firstOrNull { it.symbol == ticker }
        _uiState.update {
            it.copy(
                companyName = entry?.companyName ?: it.companyName,
                exchange = entry?.exchange ?: it.exchange,
                inWatchlist = entry != null,
                alertEnabled = entry?.alertEnabled ?: it.alertEnabled,
                tags = entry?.tags ?: emptyList()
            )
        }
    }

    private fun startHistoryObserver(ticker: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            signalsRepository.eventsForTicker(ticker).collectLatest { events ->
                _uiState.update { it.copy(history = events) }
            }
        }
    }

    private fun loadSeries(forceRefresh: Boolean = false) {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val range = _uiState.value.range
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (range == ChartRange.FIVE_DAY || range == ChartRange.ONE_MONTH || range == ChartRange.SIX_MONTH) {
                viewModelScope.launch {
                    stockRepository.refreshIntradayHistory(ticker, range)
                }
            }
            when (val result = stockRepository.getSeriesForDetail(ticker, range, forceRefresh)) {
                is Result.Success -> handleSeriesSuccess(result.data, range)
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            series = emptyList(),
                            signal = null,
                            indicators = null
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleSeriesSuccess(series: List<PriceCandle>, range: ChartRange) {
        if (series.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "No data available.",
                    series = emptyList(),
                    signal = null,
                    indicators = null
                )
            }
            return
        }

        val signal = signalsRepository.computeSignal(series, range)
        val indicators = computeIndicators(series)
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = null,
                series = series,
                signal = signal,
                indicators = indicators
            )
        }
    }

    private fun computeIndicators(series: List<PriceCandle>): TechnicalIndicators {
        val closes = series.map { it.close }
        val macd = IndicatorCalculator.macd(closes)
        return TechnicalIndicators(
            rsi14 = IndicatorCalculator.rsi(closes, 14),
            macd = macd?.macd,
            macdSignal = macd?.signal,
            macdHistogram = macd?.histogram,
            sma5 = IndicatorCalculator.sma(closes, 5),
            sma20 = IndicatorCalculator.sma(closes, 20),
            sma50 = IndicatorCalculator.sma(closes, 50),
            sma200 = IndicatorCalculator.sma(closes, 200),
            atr14 = IndicatorCalculator.atr(series, 14)
        )
    }

    private fun buildWatchlistEntry(
        tags: List<String>,
        indicatorAlertsJson: String?,
        alertEnabled: Boolean
    ): WatchlistItemEntity {
        val state = _uiState.value
        val maxSort = watchlistSnapshot.mapNotNull { it.sortOrder }.maxOrNull()
        val nextOrder = (maxSort ?: (watchlistSnapshot.size - 1)).coerceAtLeast(-1) + 1
        return WatchlistItemEntity(
            symbol = state.ticker,
            companyName = state.companyName ?: state.ticker,
            exchange = state.exchange,
            addedAt = LocalDateTime.now(),
            alertEnabled = alertEnabled,
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
            tags = tags,
            muteMarketMovers = false,
            lastNotifiedAt = null,
            indicatorAlertsJson = indicatorAlertsJson
        )
    }

    private fun mergeTags(existing: List<String>, newTag: String): List<String> {
        if (existing.any { it.equals(newTag, ignoreCase = true) }) return existing
        return existing + newTag
    }

    private data class PendingTranslation(
        val ticker: String,
        val news: List<StockNewsItem>,
        val promptType: TranslationPromptType
    )

    companion object {
        const val ARG_TICKER = "ticker"
        const val ARG_EVENT_ID = "eventId"
        const val ARG_OPEN_ALERTS = "openAlerts"
        private const val TRANSLATION_STORAGE_BYTES = 300L * 1024 * 1024
        private const val TAG = "StockDetailViewModel"
    }
}

enum class TranslationPromptType {
    PLAY_SERVICES,
    LOCAL_MODEL
}

data class StockDetailUiState(
    val ticker: String = "",
    val companyName: String? = null,
    val exchange: String? = null,
    val inWatchlist: Boolean = false,
    val alertEnabled: Boolean = false,
    val tags: List<String> = emptyList(),
    val range: ChartRange = ChartRange.ONE_DAY,
    val series: List<PriceCandle> = emptyList(),
    val signal: SignalResult? = null,
    val indicators: TechnicalIndicators? = null,
    val history: List<NotificationEvent> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val highlightEventId: String? = null,
    val indicatorAlerts: List<IndicatorAlertSetting> = emptyList(),
    val openAlerts: Boolean = false,
    val exportMessage: String? = null,
    val marketCap: Double? = null,
    val peRatio: Double? = null,
    val dividend: Double? = null,
    val week52High: Double? = null,
    val week52Low: Double? = null,
    val news: List<StockNewsItem> = emptyList(),
    val translationMessage: String? = null,
    val showTranslationPrompt: Boolean = false,
    val translationPromptTitle: String? = null,
    val translationPromptMessage: String? = null,
    val translationPromptType: TranslationPromptType? = null,
    val translationDownloadProgress: Int? = null,
    val translationDownloadInProgress: Boolean = false,
    val showTranslationRetry: Boolean = false,
    val offlineTranslationEnabled: Boolean = false,
    val localModelAvailable: Boolean = false,
    val localModelIncompatible: Boolean = false,
    val overviewError: String? = null
)
