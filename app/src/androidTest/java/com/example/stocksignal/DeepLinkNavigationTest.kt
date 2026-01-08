package com.example.stocksignal

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("AAPL").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("AAPL").fetchSemanticsNode()
    }
}
