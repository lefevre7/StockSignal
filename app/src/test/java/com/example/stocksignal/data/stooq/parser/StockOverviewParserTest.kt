package com.example.stocksignal.data.stooq.parser

import com.example.stocksignal.domain.model.StockOverview
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StockOverviewParserTest {

    @Test
    fun `parse extracts all fields from complete HTML`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>Kapitalizacja</td>
                        <td>123.45B</td>
                    </tr>
                    <tr>
                        <td>C/Z</td>
                        <td>25.30</td>
                    </tr>
                    <tr>
                        <td>Stopa dywidendy</td>
                        <td>2.15%</td>
                    </tr>
                    <tr>
                        <td>Max/min 52t</td>
                        <td>450.00 / 320.50</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals("TEST.US", result.symbol)
        assertEquals(123_450_000_000.0, result.marketCap!!, 0.01)
        assertEquals(25.30, result.peRatio!!, 0.01)
        assertEquals(2.15, result.dividend!!, 0.01)
        assertEquals(450.00, result.week52High!!, 0.01)
        assertEquals(320.50, result.week52Low!!, 0.01)
    }

    @Test
    fun `parse handles missing fields gracefully`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>C/Z</td>
                        <td>15.40</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals("TEST.US", result.symbol)
        assertNull(result.marketCap)
        assertEquals(15.40, result.peRatio!!, 0.01)
        assertNull(result.dividend)
        assertNull(result.week52High)
        assertNull(result.week52Low)
    }

    @Test
    fun `parse handles N_A values`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>Kapitalizacja</td>
                        <td>N/A</td>
                    </tr>
                    <tr>
                        <td>C/Z</td>
                        <td>-</td>
                    </tr>
                    <tr>
                        <td>Stopa dywidendy</td>
                        <td>0.00</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals("TEST.US", result.symbol)
        assertNull(result.marketCap)
        assertNull(result.peRatio)
        assertEquals(0.0, result.dividend!!, 0.01)
    }

    @Test
    fun `parse handles market cap with M suffix`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>Kapitalizacja</td>
                        <td>567.89M</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals(567_890_000.0, result.marketCap!!, 0.01)
    }

    @Test
    fun `parse handles market cap with T suffix`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>Kapitalizacja</td>
                        <td>2.5T</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals(2_500_000_000_000.0, result.marketCap!!, 0.01)
    }

    @Test
    fun `parse handles numbers with thousands separators`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>Max/min 52t</td>
                        <td>1,234.56 / 987.65</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals(1234.56, result.week52High!!, 0.01)
        assertEquals(987.65, result.week52Low!!, 0.01)
    }

    @Test
    fun `parse handles empty HTML`() {
        val result = StockOverviewParser.parse("", "TEST.US")

        assertEquals("TEST.US", result.symbol)
        assertNull(result.marketCap)
        assertNull(result.peRatio)
        assertNull(result.dividend)
        assertNull(result.week52High)
        assertNull(result.week52Low)
    }

    @Test
    fun `parse handles malformed HTML without errors`() {
        val html = "<html><body><table><tr><td>Invalid</html>"
        
        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals("TEST.US", result.symbol)
        // Should return empty overview without crashing
        assertNull(result.marketCap)
    }

    @Test
    fun `parse handles nested tables`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>Outer</td>
                        <td>Value</td>
                    </tr>
                    <tr>
                        <td>
                            <table>
                                <tr>
                                    <td>Kapitalizacja</td>
                                    <td>100.5B</td>
                                </tr>
                                <tr>
                                    <td>C/Z</td>
                                    <td>18.75</td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "NESTED.US")

        assertEquals("NESTED.US", result.symbol)
        assertEquals(100_500_000_000.0, result.marketCap!!, 0.01)
        assertEquals(18.75, result.peRatio!!, 0.01)
    }

    @Test
    fun `parse handles case insensitive labels`() {
        val html = """
            <html>
            <body>
                <table>
                    <tr>
                        <td>kapitalizacja</td>
                        <td>50M</td>
                    </tr>
                    <tr>
                        <td>c/z</td>
                        <td>12.5</td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = StockOverviewParser.parse(html, "TEST.US")

        assertEquals(50_000_000.0, result.marketCap!!, 0.01)
        assertEquals(12.5, result.peRatio!!, 0.01)
    }

    @Test
    fun `parse extracts news items from wiadomosci table`() {
        val year = LocalDate.now(ZoneId.systemDefault()).year
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
                                        <font id="f14"><a href="n/?f=1">Alpha <b>Bold</b> Beta</a></font><br>
                                        <font id="a">17 sty, 5:20 - <b>Reuters</b></font>
                                    </td>
                                </tr>
                                <tr>
                                    <td><b>·</b></td>
                                    <td>
                                        <font id="f14"><a href="/n/?f=2">Second Title</a></font><br>
                                        <font id="a">16 sty, 21:29 - <b>PAP</b></font>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2"><a href="rss/">RSS</a></td>
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
        val first = result.news[0]
        assertEquals("Alpha Beta", first.title)
        assertEquals("17 sty, 5:20", first.publishedAtText)
        
        // Verify publishedAt is converted to Instant from Poland time
        assertNotNull(first.publishedAt)
        val firstPolandTime = first.publishedAt!!.atZone(ZoneId.of("Europe/Warsaw"))
        assertEquals(year, firstPolandTime.year)
        assertEquals(1, firstPolandTime.monthValue)
        assertEquals(17, firstPolandTime.dayOfMonth)
        assertEquals(5, firstPolandTime.hour)
        assertEquals(20, firstPolandTime.minute)
        
        assertEquals("Reuters", first.source)
        assertEquals("https://stooq.com/n/?f=1", first.url)
        
        val second = result.news[1]
        assertEquals("Second Title", second.title)
        assertEquals("16 sty, 21:29", second.publishedAtText)
        
        // Verify second news item
        assertNotNull(second.publishedAt)
        val secondPolandTime = second.publishedAt!!.atZone(ZoneId.of("Europe/Warsaw"))
        assertEquals(year, secondPolandTime.year)
        assertEquals(1, secondPolandTime.monthValue)
        assertEquals(16, secondPolandTime.dayOfMonth)
        assertEquals(21, secondPolandTime.hour)
        assertEquals(29, secondPolandTime.minute)
        
        assertEquals("PAP", second.source)
        assertEquals("https://stooq.com/n/?f=2", second.url)
    }
}
