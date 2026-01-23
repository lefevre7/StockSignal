package com.example.stocksignal.data.ai

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.translation.NewsTranslationService
import com.example.stocksignal.domain.model.AiScoreReason
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.StockNewsItem
import com.example.stocksignal.domain.model.StockOverview
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiSignalScorerTest {

    @Before
    fun setup() {
        // Mock Android Log class for unit tests
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.isLoggable(any(), any()) } returns false
    }

    private fun createMockContext(): Context {
        val context = mockk<Context>()
        val activityManager = mockk<ActivityManager>()
        val memoryInfo = ActivityManager.MemoryInfo().apply {
            availMem = 2000L * 1024 * 1024 // 2GB available - enough for tests
            totalMem = 4000L * 1024 * 1024 // 4GB total
            lowMemory = false
        }
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.getMemoryInfo(any()) } answers {
            val info = it.invocation.args[0] as ActivityManager.MemoryInfo
            info.availMem = memoryInfo.availMem
            info.totalMem = memoryInfo.totalMem
            info.lowMemory = memoryInfo.lowMemory
        }
        return context
    }

    @Test
    fun `score builds prompt with required sections`() = runTest {
        val translationService = mockk<NewsTranslationService>()
        val scorer = AiSignalScorer(translationService, createMockContext())
        val promptSlot = slot<String>()
        coEvery {
            translationService.generateLocalResponse(capture(promptSlot), any(), any(), any())
        } returns validJson()

        val result = scorer.score(
            ticker = "AI_PROMPT_TEST",
            candles = sampleCandles(LocalDateTime.of(2024, 1, 1, 9, 30), 30),
            range = ChartRange.ONE_DAY,
            holdingPeriod = HoldingPeriod.DAYS,
            ruleSignal = sampleRuleSignal(LocalDateTime.of(2024, 1, 1, 10, 0)),
            overview = sampleOverview()
        )

        assertNotNull(result)
        val prompt = promptSlot.captured
        assertTrue(prompt.contains("Candle count: 30"))
        assertTrue(prompt.contains("Candle increment: 5m"))
        assertTrue(prompt.contains("Indicator stats:"))
        assertTrue(prompt.contains("RSI("))
        assertTrue(prompt.contains("Overview metrics:"))
        assertTrue(prompt.contains("Distance to 52w high/low"))
        assertTrue(prompt.contains("News (translated to English when available):"))
        assertTrue(prompt.contains("Jan 1, 2024 — Translated headline"))
    }

    @Test
    fun `score retries on invalid json then succeeds`() = runTest {
        val translationService = mockk<NewsTranslationService>()
        val scorer = AiSignalScorer(translationService, createMockContext())
        coEvery {
            translationService.generateLocalResponse(any(), any(), any(), any())
        } returnsMany listOf("not json", validJson())

        val result = scorer.score(
            ticker = "AI_RETRY_TEST",
            candles = sampleCandles(LocalDateTime.of(2024, 1, 2, 9, 30), 30),
            range = ChartRange.ONE_DAY,
            holdingPeriod = HoldingPeriod.DAYS,
            ruleSignal = sampleRuleSignal(LocalDateTime.of(2024, 1, 2, 10, 0)),
            overview = sampleOverview()
        )

        assertNotNull(result)
        coVerify(exactly = 2) { translationService.generateLocalResponse(any(), any(), any(), any()) }
    }

    @Test
    fun `score returns null after repeated invalid json`() = runTest {
        val translationService = mockk<NewsTranslationService>()
        val scorer = AiSignalScorer(translationService, createMockContext())
        coEvery {
            translationService.generateLocalResponse(any(), any(), any(), any())
        } returnsMany listOf("bad", "still bad")

        val result = scorer.score(
            ticker = "AI_INVALID_TEST",
            candles = sampleCandles(LocalDateTime.of(2024, 1, 3, 9, 30), 30),
            range = ChartRange.ONE_DAY,
            holdingPeriod = HoldingPeriod.DAYS,
            ruleSignal = sampleRuleSignal(LocalDateTime.of(2024, 1, 3, 10, 0)),
            overview = sampleOverview()
        )

        assertNull(result)
        coVerify(exactly = 2) { translationService.generateLocalResponse(any(), any(), any(), any()) }
    }

    @Test
    fun `score caches results for identical inputs`() = runTest {
        val translationService = mockk<NewsTranslationService>()
        val scorer = AiSignalScorer(translationService, createMockContext())
        coEvery { translationService.generateLocalResponse(any(), any(), any(), any()) } returns validJson()

        val candles = sampleCandles(LocalDateTime.of(2024, 1, 4, 9, 30), 30)
        val ruleSignal = sampleRuleSignal(LocalDateTime.of(2024, 1, 4, 10, 0))

        val first = scorer.score(
            ticker = "AI_CACHE_TEST",
            candles = candles,
            range = ChartRange.ONE_DAY,
            holdingPeriod = HoldingPeriod.DAYS,
            ruleSignal = ruleSignal,
            overview = sampleOverview()
        )
        val second = scorer.score(
            ticker = "AI_CACHE_TEST",
            candles = candles,
            range = ChartRange.ONE_DAY,
            holdingPeriod = HoldingPeriod.DAYS,
            ruleSignal = ruleSignal,
            overview = sampleOverview()
        )

        assertNotNull(first)
        assertNotNull(second)
        coVerify(exactly = 1) { translationService.generateLocalResponse(any(), any(), any(), any()) }
    }

    @Test
    fun `score parses fenced json output`() = runTest {
        val translationService = mockk<NewsTranslationService>()
        val scorer = AiSignalScorer(translationService, createMockContext())
        val raw = "Here you go:\\n```json\\n${validJson()}\\n```"
        coEvery { translationService.generateLocalResponse(any(), any(), any(), any()) } returns raw

        val result = scorer.score(
            ticker = "AI_FENCE_TEST",
            candles = sampleCandles(LocalDateTime.of(2024, 1, 5, 9, 30), 30),
            range = ChartRange.ONE_DAY,
            holdingPeriod = HoldingPeriod.DAYS,
            ruleSignal = sampleRuleSignal(LocalDateTime.of(2024, 1, 5, 10, 0)),
            overview = sampleOverview()
        )

        assertNotNull(result)
        assertEquals(72, result!!.score)
    }

    private fun sampleCandles(start: LocalDateTime, count: Int): List<PriceCandle> {
        return (0 until count).map { index ->
            val time = start.plusMinutes(index * 5L)
            PriceCandle(
                time = time,
                open = 100.0 + index,
                high = 101.5 + index,
                low = 99.5 + index,
                close = 100.5 + index,
                volume = 1_000L + (index * 10L)
            )
        }
    }

    private fun sampleRuleSignal(now: LocalDateTime): SignalResult {
        return SignalResult(
            score = 15,
            averageScore = 18,
            modeScore = 20,
            confidence = 62,
            aiScore = null,
            aiConfidence = null,
            aiSummary = null,
            aiReasons = emptyList(),
            reasons = listOf(
                SignalReason(
                    id = "reason_1",
                    title = "RSI oversold",
                    explanation = "RSI suggests oversold conditions.",
                    impactScore = 20,
                    model = "rsi"
                )
            ),
            modelScores = mapOf("rsi" to 20, "macd" to -5),
            generatedAt = now
        )
    }

    private fun sampleOverview(): StockOverview {
        return StockOverview(
            symbol = "AI",
            marketCap = 1_000_000_000.0,
            peRatio = 22.4,
            dividend = 0.5,
            week52High = 150.0,
            week52Low = 90.0,
            news = listOf(
                StockNewsItem(
                    title = "Oryginalny nagłówek",
                    publishedAtText = "Jan 1, 2024",
                    translatedTitle = "Translated headline",
                    translatedPublishedAtText = "Jan 1, 2024"
                )
            )
        )
    }

    private fun validJson(): String {
        return """
            {"score":72,"confidence":64,"summary":"Momentum is positive. Volatility is moderate. News tone is constructive.","reasons":[{"title":"Momentum","detail":"Price trended higher."},{"title":"Volatility","detail":"ATR is stable."},{"title":"News","detail":"Headlines skew positive."}]}
        """.trimIndent()
    }
}
