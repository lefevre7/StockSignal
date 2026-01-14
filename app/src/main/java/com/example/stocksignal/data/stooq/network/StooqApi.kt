package com.example.stocksignal.data.stooq.network

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryName

/**
 * Retrofit API interface for Stooq stock data service.
 * Fetches historical stock data from stooq.com in CSV format.
 */
interface StooqApi {

    /**
     * Fetches the Stooq home page HTML.
     *
     * URL: https://stooq.com/
     *
     * The response typically contains a protocol-relative cmp endpoint reference like:
     * `//stooq.com/cmp/?1767286282&q=`
     *
     * @return Home page HTML as a string
     */
    @GET("/")
    suspend fun getHomePage(): String

    /**
     * Fetches historical stock data for a single ticker.
     *
     * Example URL: https://stooq.com/q/d/l/?s=PKO&d1=20200401&d2=20221031&i=d
     *
     * @param ticker Stock ticker symbol (e.g., "PKO", "TPE")
     * @param startDate Start date in YYYYMMDD format (e.g., "20200401")
     * @param endDate End date in YYYYMMDD format (e.g., "20221031")
     * @param interval Data interval: "d" for daily (default)
     * @return CSV string containing stock data
     */
    @GET("q/d/l/")
    suspend fun getStockData(
        @Query("s") ticker: String,
        @Query("d1") startDate: String,
        @Query("d2") endDate: String,
        @Query("i") interval: String = "d"
    ): String

    /**
     * Fetches intraday stock data for a single ticker.
     *
     * Example URL: https://stooq.com/q/a2/d/?s=tsla.us&i=10
     *
     * The response is not pure CSV; it may include a preamble before the CSV header.
     * The repository parser finds the first `Date,Time` header and parses rows after it.
     *
     * @param ticker Stock ticker symbol (e.g., "TSLA.US")
     * @param intervalMinutes Intraday interval in minutes (e.g., 10)
     * @return Raw response string containing intraday data (CSV-like section)
     */
    @GET("q/a2/d/")
    suspend fun getIntradayData(
        @Query("s") ticker: String,
        @Query("i") intervalMinutes: Int
    ): String

    /**
     * Fetches the robots.txt file from Stooq.
     *
     * URL: https://stooq.com/robots.txt
     *
     * This file contains web crawling rules and can be used to:
     * - Understand API rate limits and allowed endpoints
     * - Check for service availability
     * - Verify connectivity to Stooq servers
     *
     * @return Content of robots.txt as a string
     */
    @GET("robots.txt")
    suspend fun getRobotsTxt(): String

    /**
     * Fetches cmp results (used for search/autocomplete).
     *
     * Example URL: https://stooq.com/cmp/?1767286282&q=tesla
     *
     * The campaign id is a "nameless" query parameter, so this uses [QueryName].
     *
     * Example response (for q=tesla):
     * `window.cmp_r('TSLA.US~Tesla Inc~XNAS~...');`
     *
     * @param campaignId Campaign id extracted from [getHomePage] response
     * @param query User-provided search text
     * @return Raw cmp response as a string
     */
    @GET("cmp/")
    suspend fun getCmp(
        @QueryName campaignId: String,
        @Query("q") query: String
    ): String

    /**
     * Fetches stock overview page HTML containing fundamental data.
     *
     * Example URL: https://stooq.com/q/g/?s=cost.us
     *
     * The response is an HTML page containing nested tables with:
     * - "Max/min 52t" → 52-week high/low
     * - "Stopa dywidendy" → Dividend
     * - "Kapitalizacja" → Market Cap
     * - "C/Z" → P/E Ratio
     *
     * @param ticker Stock ticker symbol (e.g., "cost.us", "tsla.us")
     * @return HTML page as a string
     */
    @GET("q/g/")
    suspend fun getStockOverview(
        @Query("s") ticker: String
    ): String

    /**
     * Fetches the Stooq quote page HTML for bid/ask data.
     *
     * Example URL: https://stooq.com/q/?s=nvda.us
     *
     * @param ticker Stock ticker symbol (e.g., "nvda.us")
     * @return HTML page as a string
     */
    @GET("q/")
    suspend fun getQuotePage(
        @Query("s") ticker: String
    ): String

    companion object {
        const val BASE_URL = "https://stooq.com/"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36"
        const val DEFAULT_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        const val DEFAULT_ACCEPT_ENCODING = "gzip, deflate, br, zstd"
        const val DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9,hu;q=0.8,sv;q=0.7"
        const val DEFAULT_SEC_CH_UA = "\"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\", \"Not-A.Brand\";v=\"99\""
        const val DEFAULT_SEC_CH_UA_MOBILE = "?1"
        const val DEFAULT_SEC_CH_UA_PLATFORM = "\"Android\""
        const val DEFAULT_SEC_FETCH_DEST = "document"
        const val DEFAULT_SEC_FETCH_MODE = "navigate"
        const val DEFAULT_SEC_FETCH_SITE = "none"
        const val DEFAULT_SEC_FETCH_USER = "?1"
    }
}
