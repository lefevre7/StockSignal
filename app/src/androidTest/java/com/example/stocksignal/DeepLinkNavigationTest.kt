package com.example.stocksignal

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private val activityRule = ActivityScenarioRule<MainActivity>(deepLinkIntent)

    @get:Rule
    val composeRule = AndroidComposeTestRule(activityRule) { rule ->
        var activity: MainActivity? = null
        rule.scenario.onActivity { activity = it }
        checkNotNull(activity) { "Activity was not set in ActivityScenarioRule." }
    }

    @Test
    fun deepLinkOpensStockDetail() {
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
