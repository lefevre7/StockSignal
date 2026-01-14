package com.example.stocksignal.data.stooq.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PremarketQuoteParserTest {

    @Test
    fun `parse extracts bid ask and volume from snippet`() {
        val html = """
            <table>
                <tbody>
                    <tr valign=top>
                        <td height=47 nowrap>
                            <font id=f13>Bid</font>
                            <br>
                            <font id=f13>
                                <span id=aq_nvda.us_b4>185.7800</span>
                            </font>
                            <br>
                            <font id=f10>
                                <span id=aq_nvda.us_bv1>x100</span>
                            </font>
                        </td>
                        <td>
                            <font id=f13>Ask</font>
                            <br>
                            <font id=f13>
                                <span id=aq_nvda.us_a4>185.8000</span>
                            </font>
                            <br>
                            <font id=f10>
                                <span id=aq_nvda.us_av1>x700</span>
                            </font>
                        </td>
                    </tr>
                    <tr valign=top>
                        <td height=47 id=f13>
                            Volume<br>
                            <span id=aq_nvda.us_v2>160m</span>
                        </td>
                        <td id=f13>
                            Turnover<br>
                            <span id=aq_nvda.us_r2>22.4g</span>
                        </td>
                    </tr>
                </tbody>
            </table>
        """.trimIndent()

        val quote = PremarketQuoteParser.parse(html, "NVDA.US")

        assertNotNull(quote)
        val parsed = requireNotNull(quote)
        assertEquals(185.78, requireNotNull(parsed.bid), 0.0001)
        assertEquals(185.8, requireNotNull(parsed.ask), 0.0001)
        assertEquals(160_000_000L, requireNotNull(parsed.volume))
    }
}
