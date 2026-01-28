package com.example.stocksignal.domain.signal

import com.example.stocksignal.data.settings.HoldingPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorConfigTest {

    @Test
    fun `HOURS period uses shortest indicators`() {
        val config = IndicatorConfig.forHoldingPeriod(HoldingPeriod.HOURS)
        
        assertEquals(HoldingPeriod.HOURS, config.holdingPeriod)
        assertEquals(5, config.smaShortPeriod)
        assertEquals(10, config.smaLongPeriod)
        assertEquals(5, config.macdFast)
        assertEquals(13, config.macdSlow)
        assertEquals(5, config.macdSignal)
        assertEquals(9, config.rsiPeriod)
        assertEquals(10, config.bbPeriod)
        assertEquals(2.0, config.bbStdDev, 0.01)
        assertEquals(10, config.volumeZscoreWindow)
        assertEquals(7, config.atrPeriod)
        assertEquals(10, config.breakoutWindow)
        assertEquals(10, config.rollingReturnZScoreWindow)
    }

    @Test
    fun `DAYS period uses medium-short indicators`() {
        val config = IndicatorConfig.forHoldingPeriod(HoldingPeriod.DAYS)
        
        assertEquals(HoldingPeriod.DAYS, config.holdingPeriod)
        assertEquals(5, config.smaShortPeriod)
        assertEquals(20, config.smaLongPeriod)
        assertEquals(8, config.macdFast)
        assertEquals(17, config.macdSlow)
        assertEquals(7, config.macdSignal)
        assertEquals(11, config.rsiPeriod)
        assertEquals(15, config.bbPeriod)
        assertEquals(15, config.volumeZscoreWindow)
        assertEquals(10, config.atrPeriod)
    }

    @Test
    fun `WEEKS period uses standard indicators`() {
        val config = IndicatorConfig.forHoldingPeriod(HoldingPeriod.WEEKS)
        
        assertEquals(HoldingPeriod.WEEKS, config.holdingPeriod)
        assertEquals(20, config.smaShortPeriod)
        assertEquals(50, config.smaLongPeriod)
        assertEquals(12, config.macdFast)  // Standard MACD
        assertEquals(26, config.macdSlow)
        assertEquals(9, config.macdSignal)
        assertEquals(14, config.rsiPeriod)  // Standard RSI
        assertEquals(20, config.bbPeriod)   // Standard BB
        assertEquals(2.0, config.bbStdDev, 0.01)
        assertEquals(20, config.volumeZscoreWindow)
        assertEquals(14, config.atrPeriod)
    }

    @Test
    fun `MONTHS period uses medium-term indicators`() {
        val config = IndicatorConfig.forHoldingPeriod(HoldingPeriod.MONTHS)
        
        assertEquals(HoldingPeriod.MONTHS, config.holdingPeriod)
        assertEquals(50, config.smaShortPeriod)
        assertEquals(100, config.smaLongPeriod)
        assertEquals(30, config.volumeZscoreWindow)
        assertEquals(30, config.breakoutWindow)
        assertEquals(30, config.rollingReturnZScoreWindow)
    }

    @Test
    fun `YEARS period uses longest indicators`() {
        val config = IndicatorConfig.forHoldingPeriod(HoldingPeriod.YEARS)
        
        assertEquals(HoldingPeriod.YEARS, config.holdingPeriod)
        assertEquals(50, config.smaShortPeriod)
        assertEquals(200, config.smaLongPeriod)  // Classic 50/200 SMA
        assertEquals(2.5, config.bbStdDev, 0.01) // Wider bands for long-term
        assertEquals(50, config.volumeZscoreWindow)
        assertEquals(20, config.atrPeriod)
        assertEquals(50, config.breakoutWindow)
        assertEquals(50, config.rollingReturnZScoreWindow)
    }

    @Test
    fun `indicator periods scale appropriately across holding periods`() {
        val hours = IndicatorConfig.forHoldingPeriod(HoldingPeriod.HOURS)
        val days = IndicatorConfig.forHoldingPeriod(HoldingPeriod.DAYS)
        val weeks = IndicatorConfig.forHoldingPeriod(HoldingPeriod.WEEKS)
        val months = IndicatorConfig.forHoldingPeriod(HoldingPeriod.MONTHS)
        val years = IndicatorConfig.forHoldingPeriod(HoldingPeriod.YEARS)

        // HOURS and DAYS use intraday short-term, WEEKS+ use daily longer-term
        // SMA short: HOURS=5, DAYS=5 (intraday), then WEEKS=20, MONTHS=50, YEARS=50
        assertTrue(hours.smaShortPeriod <= days.smaShortPeriod)
        assertTrue(days.smaShortPeriod < weeks.smaShortPeriod)
        assertTrue(weeks.smaShortPeriod < months.smaShortPeriod)
        assertTrue(months.smaShortPeriod <= years.smaShortPeriod)

        // SMA long: HOURS=10, DAYS=20, WEEKS=50, MONTHS=100, YEARS=200
        assertTrue(hours.smaLongPeriod < days.smaLongPeriod)
        assertTrue(days.smaLongPeriod < weeks.smaLongPeriod)
        assertTrue(weeks.smaLongPeriod < months.smaLongPeriod)
        assertTrue(months.smaLongPeriod < years.smaLongPeriod)

        // Volume windows should increase (with some equality for short periods)
        assertTrue(hours.volumeZscoreWindow <= days.volumeZscoreWindow)
        assertTrue(days.volumeZscoreWindow <= weeks.volumeZscoreWindow)
        assertTrue(weeks.volumeZscoreWindow < months.volumeZscoreWindow)
        assertTrue(months.volumeZscoreWindow < years.volumeZscoreWindow)
    }

    @Test
    fun `useIntradayData returns true for HOURS and DAYS`() {
        assertTrue(IndicatorConfig.useIntradayData(HoldingPeriod.HOURS))
        assertTrue(IndicatorConfig.useIntradayData(HoldingPeriod.DAYS))
    }

    @Test
    fun `useIntradayData returns false for WEEKS, MONTHS, YEARS`() {
        assertFalse(IndicatorConfig.useIntradayData(HoldingPeriod.WEEKS))
        assertFalse(IndicatorConfig.useIntradayData(HoldingPeriod.MONTHS))
        assertFalse(IndicatorConfig.useIntradayData(HoldingPeriod.YEARS))
    }

    @Test
    fun `all holding periods have unique configs`() {
        val configs = HoldingPeriod.entries.map { IndicatorConfig.forHoldingPeriod(it) }
        
        // Verify each period has its own distinct config
        HoldingPeriod.entries.forEachIndexed { index, period ->
            assertEquals(period, configs[index].holdingPeriod)
        }
    }

    @Test
    fun `MACD periods remain standard for longer timeframes`() {
        val weeks = IndicatorConfig.forHoldingPeriod(HoldingPeriod.WEEKS)
        val months = IndicatorConfig.forHoldingPeriod(HoldingPeriod.MONTHS)
        val years = IndicatorConfig.forHoldingPeriod(HoldingPeriod.YEARS)

        // Standard MACD parameters (12/26/9) for longer timeframes
        assertEquals(12, weeks.macdFast)
        assertEquals(26, weeks.macdSlow)
        assertEquals(9, weeks.macdSignal)

        assertEquals(12, months.macdFast)
        assertEquals(26, months.macdSlow)
        assertEquals(9, months.macdSignal)

        assertEquals(12, years.macdFast)
        assertEquals(26, years.macdSlow)
        assertEquals(9, years.macdSignal)
    }

    @Test
    fun `RSI period remains standard for longer timeframes`() {
        val weeks = IndicatorConfig.forHoldingPeriod(HoldingPeriod.WEEKS)
        val months = IndicatorConfig.forHoldingPeriod(HoldingPeriod.MONTHS)
        val years = IndicatorConfig.forHoldingPeriod(HoldingPeriod.YEARS)

        assertEquals(14, weeks.rsiPeriod)
        assertEquals(14, months.rsiPeriod)
        assertEquals(14, years.rsiPeriod)
    }

    @Test
    fun `Bollinger Bands wider for long-term`() {
        val hours = IndicatorConfig.forHoldingPeriod(HoldingPeriod.HOURS)
        val years = IndicatorConfig.forHoldingPeriod(HoldingPeriod.YEARS)

        assertEquals(2.0, hours.bbStdDev, 0.01)
        assertEquals(2.5, years.bbStdDev, 0.01)
        assertTrue(years.bbStdDev > hours.bbStdDev)
    }

    @Test
    fun `rolling return z-score window matches holding period`() {
        val expected = mapOf(
            HoldingPeriod.HOURS to 10,
            HoldingPeriod.DAYS to 15,
            HoldingPeriod.WEEKS to 20,
            HoldingPeriod.MONTHS to 30,
            HoldingPeriod.YEARS to 50
        )
        expected.forEach { (period, window) ->
            val config = IndicatorConfig.forHoldingPeriod(period)
            assertEquals(window, config.rollingReturnZScoreWindow)
        }
    }
}
