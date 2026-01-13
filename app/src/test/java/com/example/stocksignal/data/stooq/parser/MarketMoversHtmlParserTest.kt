package com.example.stocksignal.data.stooq.parser

import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarketMoversHtmlParserTest {

    @Test
    fun `parses homepage movers table`() {
        val html = sampleHomePageHtml()

        val sections = MarketMoversHtmlParser.parse(html)

        assertEquals(3, sections.size)
        val mostActive = sections.first { it.direction == MarketMoverDirection.MOST_ACTIVE }
        val advancers = sections.first { it.direction == MarketMoverDirection.INCREASERS }
        val decliners = sections.first { it.direction == MarketMoverDirection.DECREASERS }

        assertEquals(3, mostActive.items.size)
        val firstActive = mostActive.items.first()
        assertEquals("KGH", firstActive.ticker)
        assertEquals("KGHM", firstActive.companyName)
        assertEquals(280.3, firstActive.price ?: 0.0, 0.001)
        assertEquals(-4.79, firstActive.percentChange ?: 0.0, 0.001)
        assertEquals(1, firstActive.rank)

        val firstAdvancer = advancers.items.first()
        assertEquals("CPA", firstAdvancer.ticker)
        assertEquals(50.29, firstAdvancer.percentChange ?: 0.0, 0.001)

        val lastDecliner = decliners.items.last()
        assertEquals("CPR", lastDecliner.ticker)
        assertEquals(1.12, lastDecliner.price ?: 0.0, 0.001)
        assertEquals(-6.67, lastDecliner.percentChange ?: 0.0, 0.001)

        sections.forEach { section ->
            assertTrue(section.items.all { it.rank != null })
        }
        assertTrue(sections.none { section -> section.items.any { it.ticker == "OUT" } })
    }

    private fun sampleHomePageHtml(): String {
        return """
            <html>
            <body>
            <table id=outer width=100% border=0 cellpadding=0 cellspacing=0>
                <tr>
                    <td>
                        <table id=inner width=100% border=0 cellpadding=0 cellspacing=0>
                            <tbody align=right>
                            <tr>
                                <td colspan=4 align=left id=f12>
                                    <b>Najbardziej aktywne</b>
                                </td>
                            </tr>
                <tr>
                    <td align=left id=f13>
                        <a href=q/?s= kgh>KGH</a>
                    </td>
                    <td align=left id=f10>KGHM</td>
                    <td id=f13><span id=aq_kgh_c1>280.3</span></td>
                    <td id=f13><span id=aq_kgh_m1><font id=c2>-4.79%</font></span></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= pkn>PKN</a></td>
                    <td align=left id=f10>PKNORLEN</td>
                    <td id=f13><span id=aq_pkn_c2>92.58</span></td>
                    <td id=f13><span id=aq_pkn_m1><font id=c2>-7.07%</font></span></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= dnp>DNP</a></td>
                    <td align=left id=f10>DINOPL</td>
                    <td id=f13><span id=aq_dnp_c2>41.44</span></td>
                    <td id=f13><span id=aq_dnp_m1><font id=c2>-2.19%</font></span></td>
                </tr>
                <tr>
                    <td colspan=4 align=left id=f12><b>Advancers</b></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= cpa>CPA</a></td>
                    <td align=left id=f10>CAPITAL</td>
                    <td id=f13><span id=aq_cpa_c3>1.300</span></td>
                    <td id=f13><span id=aq_cpa_m1><font id=c1>+50.29%</font></span></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= trk>TRK</a></td>
                    <td align=left id=f10>TRAKCJA</td>
                    <td id=f13><span id=aq_trk_c3>4.600</span></td>
                    <td id=f13><span id=aq_trk_m1><font id=c1>+12.75%</font></span></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= snk>SNK</a></td>
                    <td align=left id=f10>SANOK</td>
                    <td id=f13><span id=aq_snk_c2>22.70</span></td>
                    <td id=f13><span id=aq_snk_m1><font id=c1>+5.58%</font></span></td>
                </tr>
                <tr>
                    <td colspan=4 align=left id=f12><b>Decliners</b></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= anr>ANR</a></td>
                    <td align=left id=f10>ANSWEAR</td>
                    <td id=f13><span id=aq_anr_c2>23.80</span></td>
                    <td id=f13><span id=aq_anr_m1><font id=c2>-11.03%</font></span></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= pkn>PKN</a></td>
                    <td align=left id=f10>PKNORLEN</td>
                    <td id=f13><span id=aq_pkn_c2>92.58</span></td>
                    <td id=f13><span id=aq_pkn_m1><font id=c2>-7.07%</font></span></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= cpr>CPR</a></td>
                    <td align=left id=f10>COMPREMUM</td>
                    <td id=f13><span id=aq_cpr_c3>1,120</span></td>
                    <td id=f13><span id=aq_cpr_m1><font id=c2>-6,67%</font></span></td>
                </tr>
                            </tbody>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= OUT>OUT</a></td>
                    <td align=left id=f10>OUTSIDE</td>
                    <td id=f13><span id=aq_out_c1>999.9</span></td>
                    <td id=f13><span id=aq_out_m1><font id=c1>+1.23%</font></span></td>
                </tr>
            </table>
            </body>
            </html>
        """.trimIndent()
    }
}
