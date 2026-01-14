package com.example.stocksignal.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun completeOnboarding(holdingPeriod: HoldingPeriod) {
        viewModelScope.launch {
            settingsRepository.setHoldingPeriod(holdingPeriod)
            settingsRepository.setOnboardingCompleted(true)
        }
    }
}
