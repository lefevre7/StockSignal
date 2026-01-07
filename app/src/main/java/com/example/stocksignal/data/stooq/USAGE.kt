package com.example.stocksignal.data.stooq

/**
 * USAGE EXAMPLE - VERIFIED WITH LIVE API
 * 
 * This demonstrates how to use the ported PyStooq library in Kotlin.
 * All examples have been tested against the real Stooq API.
 * 
 * Original Python code:
 * ```python
 * from pystooq import StooqDataFetcher
 * from datetime import date
 * 
 * fetcher = StooqDataFetcher()
 * data_df = fetcher.get_data(
 *     tickers=["PKO", "TPE"],
 *     start=date(2020, 4, 1),
 *     end=date(2022, 10, 31)
 * )
 * ```
 * 
 * Kotlin equivalent:
 * ```kotlin
 * import com.example.stocksignal.data.stooq.repository.StooqRepository
 * import com.example.stocksignal.data.stooq.model.Result
 * import com.example.stocksignal.data.stooq.model.IntradayStockData
 * import org.koin.android.ext.android.inject
 * import kotlinx.coroutines.launch
 * import java.time.LocalDate
 * import java.time.LocalDateTime
 * 
 * class MyActivity : AppCompatActivity() {
 *     
 *     // Inject repository using Koin
 *     private val stooqRepository: StooqRepository by inject()
 *     
 *     fun fetchStockData() {
 *         lifecycleScope.launch {
 *             // IMPORTANT: US tickers need ".US" suffix, Polish tickers don't
 *             val result = stooqRepository.getData(
 *                 tickers = listOf("AAPL.US", "MSFT.US"),
 *                 startDate = LocalDate.of(2024, 1, 2),
 *                 endDate = LocalDate.of(2024, 1, 31)
 *             )
 *             
 *             when (result) {
 *                 is Result.Success -> {
 *                     val data = result.data
 *                     // Access data for AAPL ticker
 *                     val aaplData = data["AAPL.US"]
 *                     
 *                     // Get data for specific date
 *                     val jan2Data = aaplData?.get(LocalDate.of(2024, 1, 2))
 *                     jan2Data?.let { stockData ->
 *                         Log.d("Stock", "Open: ${stockData.open}")
 *                         Log.d("Stock", "High: ${stockData.high}")
 *                         Log.d("Stock", "Low: ${stockData.low}")
 *                         Log.d("Stock", "Close: ${stockData.close}")
 *                         Log.d("Stock", "Volume: ${stockData.volume}")
 *                     }
 *                     
 *                     // Iterate through all dates for a ticker
 *                     aaplData?.forEach { (date, stockData) ->
 *                         Log.d("Stock", "$date - Close: ${stockData.close}")
 *                     }
 *                     
 *                     // Iterate through all tickers
 *                     data.forEach { (ticker, dateMap) ->
 *                         Log.d("Stock", "Ticker: $ticker has ${dateMap.size} data points")
 *                     }
 *                 }
 *                 
 *                 is Result.Error -> {
 *                     Log.e("Stock", "Error fetching data: ${result.message}", result.exception)
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * INTRADAY USAGE (example):
 * ```kotlin
 * lifecycleScope.launch {
 *     val result = stooqRepository.getIntradayData(
 *         tickers = listOf("TSLA.US"),
 *         intervalMinutes = 10,
 *         start = null,
 *         end = null
 *     )
 *
 *     when (result) {
 *         is Result.Success -> {
 *             val tslaData: Map<LocalDateTime, IntradayStockData>? = result.data["TSLA.US"]
 *             val latest = tslaData?.entries?.lastOrNull()
 *             println("TSLA.US intraday points: ${tslaData?.size}, latest: $latest")
 *         }
 *         is Result.Error -> {
 *             println("Intraday error: ${result.message}")
 *         }
 *     }
 * }
 * ```
 * 
 * REAL API RESPONSE FORMAT (verified):
 * ```
 * Date,Open,High,Low,Close,Volume
 * 2024-01-02,186.032,187.316,182.788,184.532,82983926
 * 2024-01-03,183.121,184.771,182.335,183.151,58765173
 * 2024-01-04,181.064,181.995,179.799,180.824,72415750
 * ```
 * 
 * Data Structure Comparison:
 * 
 * Python (pandas DataFrame with MultiIndex):
 * ```
 * ticker          PKO                                             TPE
 * variable       open     high      low    close        volume   open   high
 * date
 * 2020-04-01  20.8531  20.9742  20.4063  20.6669  3.176696e+06  1.100  1.113
 * ```
 * 
 * Kotlin (Map<String, Map<LocalDate, StockData>>):
 * ```
 * {
 *   "AAPL.US" -> {
 *     2024-01-02 -> StockData(date=2024-01-02, open=186.032, high=187.316, 
 *                             low=182.788, close=184.532, volume=82983926)
 *   },
 *   "MSFT.US" -> {
 *     2024-01-02 -> StockData(date=2024-01-02, open=..., high=..., ...)
 *   }
 * }
 * ```
 * 
 * IMPORTANT TICKER FORMAT NOTES:
 * - US stocks require ".US" suffix: "AAPL.US", "MSFT.US", "TSLA.US"
 * - Polish stocks use plain format: "PKO", "CDR"
 * - Check stooq.com for correct ticker format for other markets
 * 
 * Features:
 * - ✅ Parallel fetching of multiple tickers using coroutines
 * - ✅ Graceful error handling with Result sealed class
 * - ✅ Automatic CSV parsing with Apache Commons CSV
 * - ✅ Type-safe data structures with Kotlin data classes
 * - ✅ Dependency injection with Koin
 * - ✅ Comprehensive logging using Android Log
 * - ✅ Unit tests with JUnit and MockK
 * - ✅ Live integration tests verified against real API
 * - ✅ Modern Kotlin coroutines for async operations
 * 
 * See StooqExampleActivity.kt for complete working examples
 */
