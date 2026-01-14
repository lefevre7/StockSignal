package com.example.stocksignal

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.settingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that deep links navigate to the correct screen.
 * Note: These tests verify navigation occurs, not that data loads successfully
 * (which would require mocking repositories).
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkNavigationTest {

    private val deepLinkIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("stocksignal://stock/AAPL?eventId=evt_1")
    )

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deepLinkOpensStockDetail() {
        runBlocking {
            val settingsRepository = SettingsRepository(composeRule.activity.settingsDataStore)
            settingsRepository.setOnboardingCompleted(true)
        }
        composeRule.activity.runOnUiThread {
            composeRule.activity.handleNewIntent(deepLinkIntent)
        }

        // Wait for composition to complete and verify we're on stock detail screen
        // by checking for the back navigation button
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("Back")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        
        // Verify the back button is displayed (confirms we navigated to detail screen)
        composeRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }
}
