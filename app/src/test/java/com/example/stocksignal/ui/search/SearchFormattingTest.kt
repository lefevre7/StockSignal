package com.example.stocksignal.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFormattingTest {

    @Test
    fun `formatPrice handles different magnitudes`() {
        assertEquals("$432.75", formatPrice(432.75))
        assertEquals("$0.123", formatPrice(0.1234))
        assertEquals("$0.009876", formatPrice(0.009876))
    }

    @Test
    fun `formatPrice handles null`() {
        assertEquals("—", formatPrice(null))
    }

    @Test
    fun `formatPercentChange includes sign`() {
        assertEquals("+0.43%", formatPercentChange(0.43))
        assertEquals("-1.20%", formatPercentChange(-1.2))
        assertEquals("0.00%", formatPercentChange(0.0))
    }

    @Test
    fun `formatPercentChange handles null`() {
        assertEquals("—", formatPercentChange(null))
    }
}
