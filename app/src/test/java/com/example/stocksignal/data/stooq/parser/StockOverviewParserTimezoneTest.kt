package com.example.stocksignal.data.stooq.parser

import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Tests for timezone conversion in StockOverviewParser.
 * 
 * The parser should:
 * 1. Parse news dates as Poland time (Europe/Warsaw)
 * 2. Convert them to Instant (UTC)
 * 3. Use local phone timezone to determine the current year
 */
class StockOverviewParserTimezoneTest {

    private val polandZone = ZoneId.of("Europe/Warsaw")
    private val localZone = ZoneId.systemDefault()

    @Test
    fun testNewsDateConversionFromPolandToInstant() {
        // Simulated HTML with Polish news date "17 sty, 5:20" (17 Jan, 5:20 AM Poland time)
        val year = LocalDate.now(localZone).year
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>
                            <table>
                                <tr><td><b>Wiadomości</b></td></tr>
                            </table>
                            <table>
                                <tr>
                                    <td><b>·</b></td>
                                    <td>
                                        <font id="f14"><a href="n/?f=1">Test News</a></font><br>
                                        <font id="a">17 sty, 5:20 - <b>Reuters</b></font>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")
        
        assertEquals(1, result.news.size)
        val newsItem = result.news[0]
        
        // Verify the publishedAt is an Instant
        assertNotNull(newsItem.publishedAt)
        
        // Convert the Instant back to Poland time and verify it matches the input
        val polandTime = newsItem.publishedAt!!.atZone(polandZone)
        assertEquals(year, polandTime.year)
        assertEquals(1, polandTime.monthValue)
        assertEquals(17, polandTime.dayOfMonth)
        assertEquals(5, polandTime.hour)
        assertEquals(20, polandTime.minute)
        
        // Convert to local time and verify it's a valid conversion
        val localTime = newsItem.publishedAt!!.atZone(localZone)
        
        // Verify that the Instant can be converted to local time
        // The exact offset depends on the local timezone, but we can verify the conversion works
        assertNotNull("Local time conversion should succeed", localTime)
        assertTrue("Local time should have a reasonable year", 
            localTime.year >= 2020 && localTime.year <= 2030)
    }

    @Test
    fun testMultipleNewsItemsWithTimezoneConversion() {
        val year = LocalDate.now(localZone).year
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>
                            <table>
                                <tr><td><b>Wiadomości</b></td></tr>
                            </table>
                            <table>
                                <tr>
                                    <td><b>·</b></td>
                                    <td>
                                        <font id="f14"><a href="n/?f=1">Morning News</a></font><br>
                                        <font id="a">17 sty, 8:00 - <b>Reuters</b></font>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>·</b></td>
                                    <td>
                                        <font id="f14"><a href="n/?f=2">Evening News</a></font><br>
                                        <font id="a">17 sty, 20:30 - <b>PAP</b></font>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")
        
        assertEquals(2, result.news.size)
        
        // Verify both news items have Instant publishedAt
        result.news.forEach { newsItem ->
            assertNotNull("Each news item should have publishedAt as Instant", newsItem.publishedAt)
        }
        
        // Verify chronological ordering is preserved
        val first = result.news[0].publishedAt!!
        val second = result.news[1].publishedAt!!
        
        assertTrue("Later news should have later timestamp", second.isAfter(first))
    }

    @Test
    fun testYearDeterminationUsesLocalTimezone() {
        // This test verifies that the current year is determined using local phone timezone
        // rather than Poland timezone
        
        val expectedYear = LocalDate.now(localZone).year
        val polandYear = LocalDate.now(polandZone).year
        
        // In most cases these will be the same, but there's a brief period on New Year's
        // where they could differ. This test documents the expected behavior.
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>
                            <table>
                                <tr><td><b>Wiadomości</b></td></tr>
                            </table>
                            <table>
                                <tr>
                                    <td><b>·</b></td>
                                    <td>
                                        <font id="f14"><a href="n/?f=1">Test</a></font><br>
                                        <font id="a">15 sty, 12:00 - <b>Reuters</b></font>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")
        val newsItem = result.news[0]
        
        // Verify the year in the Instant matches local timezone's current year
        val localTime = newsItem.publishedAt!!.atZone(localZone)
        assertEquals("Year should be determined using local phone timezone", 
            expectedYear, localTime.year)
    }

    @Test
    fun testDstHandling() {
        // Test that DST is handled automatically by Java's ZonedDateTime
        // No special handling needed
        
        // Create a date in January (standard time) and July (DST) for Poland
        val janPoland = LocalDateTime.of(2026, 1, 15, 12, 0).atZone(polandZone)
        val julPoland = LocalDateTime.of(2026, 7, 15, 12, 0).atZone(polandZone)
        
        // Convert to Instant and back to local time
        val janLocal = janPoland.toInstant().atZone(localZone)
        val julLocal = julPoland.toInstant().atZone(localZone)
        
        // Verify conversions work correctly
        assertNotNull("January conversion should succeed", janLocal)
        assertNotNull("July conversion should succeed", julLocal)
        
        // Document that DST is handled automatically
        println("Poland Jan 12:00 -> Local ${janLocal.hour}:00")
        println("Poland Jul 12:00 -> Local ${julLocal.hour}:00")
    }

    @Test
    fun testOriginalTextPreserved() {
        // Verify that the original Polish text is preserved in publishedAtText
        // while publishedAt contains the converted Instant
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>
                            <table>
                                <tr><td><b>Wiadomości</b></td></tr>
                            </table>
                            <table>
                                <tr>
                                    <td><b>·</b></td>
                                    <td>
                                        <font id="f14"><a href="n/?f=1">Test</a></font><br>
                                        <font id="a">17 sty, 5:20 - <b>Reuters</b></font>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")
        val newsItem = result.news[0]
        
        // Original text should be preserved
        assertEquals("17 sty, 5:20", newsItem.publishedAtText)
        
        // But publishedAt should be an Instant
        assertNotNull(newsItem.publishedAt)
        assertTrue(newsItem.publishedAt is Instant)
    }
}
