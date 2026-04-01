package com.example.stocksignal.ui.onboarding

import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.translation.NewsTranslationService
import com.example.stocksignal.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val translationService = mockk<NewsTranslationService>()

    @Test
    fun `isModelAlreadyAvailable reflects file existence`() {
        val file = File.createTempFile("stocksignal-model", ".litertlm")
        every { translationService.getLocalModelFilePath() } returns file.absolutePath

        val viewModel = OnboardingViewModel(settingsRepository, translationService)

        assertTrue(viewModel.isModelAlreadyAvailable())
        file.delete()
        assertFalse(viewModel.isModelAlreadyAvailable())
    }

    @Test
    fun `downloadModel completes on success and retry resets state`() = runTest(mainDispatcherRule.dispatcher) {
        every { translationService.getLocalModelFilePath() } returns "/tmp/missing-model.litertlm"
        coEvery { translationService.downloadLocalModel(any()) } answers {
            val progress = firstArg<(Int) -> Unit>()
            progress(25)
            progress(100)
            true
        }
        val viewModel = OnboardingViewModel(settingsRepository, translationService)

        viewModel.downloadModel()
        advanceUntilIdle()
        assertEquals(ModelDownloadState(isDownloading = false, progress = 100, isComplete = true, error = null), viewModel.modelDownloadState.value)

        viewModel.retryDownload()
        advanceUntilIdle()
        assertTrue(viewModel.modelDownloadState.value.isComplete)
        coVerify(exactly = 2) { translationService.downloadLocalModel(any()) }
    }

    @Test
    fun `downloadModel surfaces failure and exception messages`() = runTest(mainDispatcherRule.dispatcher) {
        every { translationService.getLocalModelFilePath() } returns "/tmp/missing-model.litertlm"
        coEvery { translationService.downloadLocalModel(any()) } returns false
        val viewModel = OnboardingViewModel(settingsRepository, translationService)

        viewModel.downloadModel()
        advanceUntilIdle()
        assertEquals(
            "Download failed. Please check your connection and try again.",
            viewModel.modelDownloadState.value.error
        )

        coEvery { translationService.downloadLocalModel(any()) } throws IllegalStateException("network down")
        viewModel.retryDownload()
        advanceUntilIdle()
        assertEquals("network down", viewModel.modelDownloadState.value.error)
    }

    @Test
    fun `completeOnboarding persists holding period and completion`() = runTest(mainDispatcherRule.dispatcher) {
        every { translationService.getLocalModelFilePath() } returns "/tmp/missing-model.litertlm"
        val viewModel = OnboardingViewModel(settingsRepository, translationService)

        viewModel.completeOnboarding(HoldingPeriod.WEEKS)
        advanceUntilIdle()

        coVerify { settingsRepository.setHoldingPeriod(HoldingPeriod.WEEKS) }
        coVerify { settingsRepository.setOnboardingCompleted(true) }
    }
}
