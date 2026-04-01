package com.example.stocksignal.data.stooq.repository

import com.example.stocksignal.data.local.dao.MarketMoversCacheDao
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity
import com.example.stocksignal.data.local.repository.MarketMoversCacheRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.network.StooqApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarketMoversRepositoryTest {

    @Test
    fun `getMarketMovers parses homepage and caches result`() = runTest {
        val cacheDao = InMemoryMarketMoversCacheDao()
        val cacheRepository = MarketMoversCacheRepository(cacheDao)
        val api = FakeStooqApi(sampleHomePageHtml())
        val repository = MarketMoversRepository(api, cacheRepository)

        val result = repository.getMarketMovers(
            range = MarketMoverRange.ONE_DAY,
            direction = MarketMoverDirection.MOST_ACTIVE,
            forceRefresh = true
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(3, data.items.size)
        assertEquals("KGH", data.items.first().ticker)
        assertTrue(data.items.none { it.ticker == "OUT" })

        val cached = cacheDao.getCache("1D", "MOST_ACTIVE")
        assertNotNull(cached)
        assertEquals(3, cached?.items?.size)
    }

    @Test
    fun `getMarketMoversBatch fetches homepage once for multiple directions`() = runTest {
        val cacheDao = InMemoryMarketMoversCacheDao()
        val cacheRepository = MarketMoversCacheRepository(cacheDao)
        val api = FakeStooqApi(sampleHomePageHtml())
        val repository = MarketMoversRepository(api, cacheRepository)

        val result = repository.getMarketMoversBatch(
            range = MarketMoverRange.ONE_DAY,
            directions = setOf(
                MarketMoverDirection.INCREASERS,
                MarketMoverDirection.DECREASERS
            ),
            forceRefresh = true
        )

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, api.homePageCalls)
        assertEquals(1, data.snapshots[MarketMoverDirection.INCREASERS]?.items?.size)
        assertEquals(1, data.snapshots[MarketMoverDirection.DECREASERS]?.items?.size)
    }

    private class FakeStooqApi(private val html: String) : StooqApi {
        var homePageCalls: Int = 0
            private set

        override suspend fun getHomePage(): String {
            homePageCalls += 1
            return html
        }

        override suspend fun getStockData(
            ticker: String,
            startDate: String,
            endDate: String,
            interval: String
        ): String {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun getIntradayData(ticker: String, intervalMinutes: Int): String {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun getRobotsTxt(): String {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun getCmp(campaignId: String, query: String): String {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun getStockOverview(ticker: String): String {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun getQuotePage(ticker: String): String {
            throw UnsupportedOperationException("Not used in this test")
        }
    }

    private class InMemoryMarketMoversCacheDao : MarketMoversCacheDao {
        private val storage = mutableMapOf<Pair<String, String>, MarketMoversCacheEntity>()

        override suspend fun getCache(range: String, direction: String): MarketMoversCacheEntity? {
            return storage[range to direction]
        }

        override suspend fun upsert(cache: MarketMoversCacheEntity) {
            storage[cache.range to cache.direction] = cache
        }

        override suspend fun delete(range: String, direction: String) {
            storage.remove(range to direction)
        }
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
                                <td colspan=4 align=left id=f12><b>Najbardziej aktywne</b></td>
                            </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= kgh>KGH</a></td>
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
                    <td colspan=4 align=left id=f12><b>Decliners</b></td>
                </tr>
                <tr>
                    <td align=left id=f13><a href=q/?s= anr>ANR</a></td>
                    <td align=left id=f10>ANSWEAR</td>
                    <td id=f13><span id=aq_anr_c2>23.80</span></td>
                    <td id=f13><span id=aq_anr_m1><font id=c2>-11.03%</font></span></td>
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
