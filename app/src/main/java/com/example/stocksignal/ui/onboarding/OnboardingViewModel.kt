package com.example.stocksignal.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.translation.NewsTranslationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelDownloadState(
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val translationService: NewsTranslationService
) : ViewModel() {

    private val _modelDownloadState = MutableStateFlow(ModelDownloadState())
    val modelDownloadState: StateFlow<ModelDownloadState> = _modelDownloadState.asStateFlow()

    fun isModelAlreadyAvailable(): Boolean {
        // Check synchronously if model file exists
        return translationService.getLocalModelFilePath().let { path ->
            java.io.File(path).exists()
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _modelDownloadState.value = ModelDownloadState(isDownloading = true, progress = 0)
            
            try {
                val success = translationService.downloadLocalModel { progress ->
                    _modelDownloadState.value = ModelDownloadState(
                        isDownloading = true,
                        progress = progress
                    )
                }
                
                if (success) {
                    _modelDownloadState.value = ModelDownloadState(
                        isDownloading = false,
                        progress = 100,
                        isComplete = true
                    )
                } else {
                    _modelDownloadState.value = ModelDownloadState(
                        isDownloading = false,
                        progress = 0,
                        error = "Download failed. Please check your connection and try again."
                    )
                }
            } catch (e: Exception) {
                _modelDownloadState.value = ModelDownloadState(
                    isDownloading = false,
                    progress = 0,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun retryDownload() {
        _modelDownloadState.value = ModelDownloadState()
        downloadModel()
    }

    fun completeOnboarding(holdingPeriod: HoldingPeriod) {
        viewModelScope.launch {
            settingsRepository.setHoldingPeriod(holdingPeriod)
            settingsRepository.setOnboardingCompleted(true)
        }
    }
}
