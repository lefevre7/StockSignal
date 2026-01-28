package com.example.stocksignal.data.stooq.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CmpParserTest {

    @Test
    fun `parse - handles empty input`() {
        val results = CmpParser.parse("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse - handles blank input`() {
        val results = CmpParser.parse("   ")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse - parses single valid result`() {
        val input = "window.cmp_r('AAPL.US~Apple Inc~XNAS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("AAPL.US", results[0].symbol)
        assertEquals("Apple Inc", results[0].companyName)
        assertEquals("XNAS", results[0].exchange)
    }

    @Test
    fun `parse - extracts price and percent change`() {
        val input = "window.cmp_r('TSLA.US~Tesla Inc~XNAS~432.7500~0.43%~4');"
        val results = CmpParser.parse(input)

        assertEquals(1, results.size)
        assertEquals("TSLA.US", results[0].symbol)
        assertEquals(432.75, results[0].price!!, 0.0001)
        assertEquals(0.43, results[0].percentChange!!, 0.0001)
    }

    @Test
    fun `parse - handles negative percent change`() {
        val input = "window.cmp_r('META.US~Meta Platforms~XNAS~287.1200~-1.25%~4');"
        val results = CmpParser.parse(input)

        assertEquals(1, results.size)
        assertEquals(-1.25, results[0].percentChange!!, 0.0001)
    }

    @Test
    fun `parse - handles missing price and percent change`() {
        val input = "window.cmp_r('AAPL.US~Apple Inc~XNAS');"
        val results = CmpParser.parse(input)

        assertEquals(1, results.size)
        assertEquals(null, results[0].price)
        assertEquals(null, results[0].percentChange)
    }

    @Test
    fun `parse - parses multiple valid results`() {
        val input = """
            window.cmp_r('AAPL.US~Apple Inc~XNAS');
            window.cmp_r('MSFT.US~Microsoft Corporation~XNAS');
        """.trimIndent()
        val results = CmpParser.parse(input)
        
        assertEquals(2, results.size)
        assertEquals("AAPL.US", results[0].symbol)
        assertEquals("MSFT.US", results[1].symbol)
    }

    @Test
    fun `parse - strips HTML tags from symbol`() {
        val input = "window.cmp_r('<b>VGT</b>.US~Vanguard Information Technology ETF~XNAS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("VGT.US", results[0].symbol)
        assertEquals("Vanguard Information Technology ETF", results[0].companyName)
    }

    @Test
    fun `parse - strips malformed HTML tags from symbol`() {
        // User's example: <b>VGT<b>.US
        val input = "window.cmp_r('<b>VGT<b>.US~Vanguard Information Technology ETF~XNAS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("VGT.US", results[0].symbol)
    }

    @Test
    fun `parse - decodes HTML entities in company name`() {
        val input = "window.cmp_r('T.US~AT&amp;T Inc~XNYS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("T.US", results[0].symbol)
        assertEquals("AT&T Inc", results[0].companyName)
    }

    @Test
    fun `parse - strips HTML tags from company name`() {
        val input = "window.cmp_r('JNJ.US~<b>Johnson &amp; Johnson</b>~XNYS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("JNJ.US", results[0].symbol)
        assertEquals("Johnson & Johnson", results[0].companyName)
    }

    @Test
    fun `parse - handles mixed HTML tags and entities`() {
        val input = "window.cmp_r('<i>TSLA</i>.US~<b>Tesla</b>, Inc &amp; Co~XNAS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("TSLA.US", results[0].symbol)
        assertEquals("Tesla, Inc & Co", results[0].companyName)
    }

    @Test
    fun `parse - handles multiple entity types`() {
        val input = "window.cmp_r('TEST.US~Company &quot;Best&quot; &amp; &lt;Great&gt;~XNAS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("TEST.US", results[0].symbol)
        assertEquals("Company \"Best\" & <Great>", results[0].companyName)
    }

    @Test
    fun `parse - skips entries with blank symbol after HTML stripping`() {
        val input = "window.cmp_r('<b></b>~Company Name~XNAS');"
        val results = CmpParser.parse(input)
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse - skips entries with blank company name after HTML stripping`() {
        val input = "window.cmp_r('AAPL.US~<b></b>~XNAS');"
        val results = CmpParser.parse(input)
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parse - handles missing exchange field`() {
        val input = "window.cmp_r('AAPL.US~Apple Inc~');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("AAPL.US", results[0].symbol)
        assertEquals("Apple Inc", results[0].companyName)
        assertEquals(null, results[0].exchange)
    }

    @Test
    fun `parse - handles exchange with only whitespace`() {
        val input = "window.cmp_r('AAPL.US~Apple Inc~   ');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals(null, results[0].exchange)
    }

    @Test
    fun `parse - trims whitespace from fields`() {
        val input = "window.cmp_r('  AAPL.US  ~  Apple Inc  ~  XNAS  ');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("AAPL.US", results[0].symbol)
        assertEquals("Apple Inc", results[0].companyName)
        assertEquals("XNAS", results[0].exchange)
    }

    @Test
    fun `parse - handles payload with more than 3 fields`() {
        val input = "window.cmp_r('AAPL.US~Apple Inc~XNAS~Extra~More');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("AAPL.US", results[0].symbol)
        assertEquals("Apple Inc", results[0].companyName)
        assertEquals("XNAS", results[0].exchange)
    }

    @Test
    fun `parse - skips malformed entries without proper separator`() {
        val input = """
            window.cmp_r('VALIDAAAPL.US~Apple Inc~XNAS');
            window.cmp_r('INVALID');
            window.cmp_r('MSFT.US~Microsoft Corporation~XNAS');
        """.trimIndent()
        val results = CmpParser.parse(input)
        
        // Should only get the valid entries (INVALID has no ~ separators so companyName is blank)
        assertEquals(2, results.size)
    }

    @Test
    fun `parse - ignores non-matching lines`() {
        val input = """
            Some random text
            window.cmp_r('AAPL.US~Apple Inc~XNAS');
            More random text
            function test() {}
        """.trimIndent()
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("AAPL.US", results[0].symbol)
    }

    @Test
    fun `parse - handles complex real-world HTML example`() {
        val input = "window.cmp_r('<b>BRK-B</b>.US~<i>Berkshire Hathaway Inc</i> Class &quot;B&quot;~XNYS');"
        val results = CmpParser.parse(input)
        
        assertEquals(1, results.size)
        assertEquals("BRK-B.US", results[0].symbol)
        assertEquals("Berkshire Hathaway Inc Class \"B\"", results[0].companyName)
    }
}
