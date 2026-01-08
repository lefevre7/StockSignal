package com.example.stocksignal.data.stooq.examples

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stocksignal.MainActivity
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.repository.StooqRepository
import com.example.stocksignal.databinding.ActivityStooqExampleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Example Activity demonstrating how to use the StooqRepository.
 * 
 * This is a real working example that you can use as a template.
 * Make sure to inject StooqRepository using Koin (already configured in StockSignalApplication).
 */
@AndroidEntryPoint
class StooqExampleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStooqExampleBinding

    // Inject the repository using Hilt
    @Inject lateinit var stooqRepository: StooqRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStooqExampleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.openMainButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        
        // Example 1: Fetch single ticker
        fetchSingleTicker()
        
        // Example 2: Fetch multiple tickers
        fetchMultipleTickers()
        
        // Example 3: Process and analyze data
        analyzeStockData()

        // Example 4: Fetch intraday data
        fetchIntradayTicker()
    }

    /**
     * Example 1: Fetch data for a single stock ticker
     */
    private fun fetchSingleTicker() {
        lifecycleScope.launch {
            Log.d(TAG, "=== Example 1: Single Ticker ===")
            
            val result = stooqRepository.getData(
                tickers = listOf("AAPL.US"),
                startDate = LocalDate.of(2024, 1, 2),
                endDate = LocalDate.of(2024, 1, 5)
            )
            
            when (result) {
                is Result.Success -> {
                    val data = result.data
                    val aaplData = data["AAPL.US"]
                    
                    aaplData?.forEach { (date, stockData) ->
                        Log.d(TAG, "$date: Close=${stockData.close}, Volume=${stockData.volume}")
                    }
                }
                is Result.Error -> {
                    Log.e(TAG, "Error: ${result.message}", result.exception)
                }
            }
        }
    }

    /**
     * Example 2: Fetch multiple tickers in parallel
     */
    private fun fetchMultipleTickers() {
        lifecycleScope.launch {
            Log.d(TAG, "=== Example 2: Multiple Tickers ===")
            
            val result = stooqRepository.getData(
                tickers = listOf("AAPL.US", "GOOGL.US", "TSLA.US"),
                startDate = LocalDate.of(2024, 1, 2),
                endDate = LocalDate.of(2024, 1, 3)
            )
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { (ticker, dateMap) ->
                        Log.d(TAG, "$ticker: ${dateMap.size} data points")
                        
                        // Get the latest date's data
                        val latestDate = dateMap.keys.maxOrNull()
                        latestDate?.let { date ->
                            val stockData = dateMap[date]
                            Log.d(TAG, "  Latest ($date): Close=${stockData?.close}")
                        }
                    }
                }
                is Result.Error -> {
                    Log.e(TAG, "Error: ${result.message}", result.exception)
                }
            }
        }
    }

    /**
     * Example 3: Analyze stock data - calculate price change percentage
     */
    private fun analyzeStockData() {
        lifecycleScope.launch {
            Log.d(TAG, "=== Example 3: Stock Analysis ===")
            
            val result = stooqRepository.getData(
                tickers = listOf("AAPL.US"),
                startDate = LocalDate.of(2024, 1, 2),
                endDate = LocalDate.of(2024, 1, 31)
            )
            
            when (result) {
                is Result.Success -> {
                    val aaplData = result.data["AAPL.US"]
                    
                    if (aaplData != null && aaplData.isNotEmpty()) {
                        val sortedDates = aaplData.keys.sorted()
                        val firstDate = sortedDates.first()
                        val lastDate = sortedDates.last()
                        
                        val firstPrice = aaplData[firstDate]?.close ?: 0.0
                        val lastPrice = aaplData[lastDate]?.close ?: 0.0
                        
                        val priceChange = lastPrice - firstPrice
                        val percentChange = (priceChange / firstPrice) * 100
                        
                        Log.d(TAG, "Period: $firstDate to $lastDate")
                        Log.d(TAG, "Start Price: $$firstPrice")
                        Log.d(TAG, "End Price: $$lastPrice")
                        Log.d(TAG, "Change: $${"%.2f".format(priceChange)} (${("%.2f".format(percentChange))}%)")
                        
                        // Find highest and lowest prices
                        val highestDay = aaplData.maxByOrNull { it.value.high }
                        val lowestDay = aaplData.minByOrNull { it.value.low }
                        
                        Log.d(TAG, "Highest: ${highestDay?.value?.high} on ${highestDay?.key}")
                        Log.d(TAG, "Lowest: ${lowestDay?.value?.low} on ${lowestDay?.key}")
                        
                        // Calculate average volume
                        val avgVolume = aaplData.values.map { it.volume }.average()
                        Log.d(TAG, "Average Volume: ${avgVolume.toLong()}")
                    }
                }
                is Result.Error -> {
                    Log.e(TAG, "Error: ${result.message}", result.exception)
                }
            }
        }
    }

    /**
     * Example 4: Fetch intraday data (e.g., 10-minute intervals)
     */
    private fun fetchIntradayTicker() {
        lifecycleScope.launch {
            Log.d(TAG, "=== Example 4: Intraday (10m) ===")

            val ticker = "TSLA.US"
            val result = stooqRepository.getIntradayData(
                tickers = listOf(ticker),
                intervalMinutes = 10
            )

            when (result) {
                is Result.Success -> {
                    val data = result.data[ticker]
                    if (data.isNullOrEmpty()) {
                        Log.w(TAG, "No intraday data returned for $ticker")
                        return@launch
                    }

                    val firstEntry = data.entries.first()
                    val lastEntry = data.entries.last()

                    Log.d(TAG, "$ticker intraday points: ${data.size}")
                    Log.d(
                        TAG,
                        "First: ${firstEntry.key} Close=${firstEntry.value.close} Vol=${firstEntry.value.volume}"
                    )
                    Log.d(
                        TAG,
                        "Last:  ${lastEntry.key} Close=${lastEntry.value.close} Vol=${lastEntry.value.volume}"
                    )
                }

                is Result.Error -> {
                    Log.e(TAG, "Error fetching intraday data: ${result.message}", result.exception)
                }
            }
        }
    }

    companion object {
        private const val TAG = "StooqExample"
    }
}

/**
 * Simpler standalone example functions that can be called from anywhere
 */
object StooqExamples {
    
    /**
     * Simple example: Get latest closing price for a ticker
     */
    suspend fun getLatestPrice(
        repository: StooqRepository,
        ticker: String
    ): Double? {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(7) // Last week
        
        val result = repository.getData(
            tickers = listOf(ticker),
            startDate = startDate,
            endDate = endDate
        )
        
        return when (result) {
            is Result.Success -> {
                val tickerData = result.data[ticker]
                val latestDate = tickerData?.keys?.maxOrNull()
                tickerData?.get(latestDate)?.close
            }
            is Result.Error -> null
        }
    }
    
    /**
     * Compare performance of multiple stocks
     */
    suspend fun compareStocks(
        repository: StooqRepository,
        tickers: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Double> {
        val result = repository.getData(tickers, startDate, endDate)
        
        return when (result) {
            is Result.Success -> {
                result.data.mapValues { (_, dateMap) ->
                    if (dateMap.isEmpty()) return@mapValues 0.0
                    
                    val sortedDates = dateMap.keys.sorted()
                    val firstPrice = dateMap[sortedDates.first()]?.close ?: 0.0
                    val lastPrice = dateMap[sortedDates.last()]?.close ?: 0.0
                    
                    if (firstPrice == 0.0) 0.0
                    else ((lastPrice - firstPrice) / firstPrice) * 100
                }
            }
            is Result.Error -> emptyMap()
        }
    }
}

/**
 * COMMON TICKER FORMATS:
 * 
 * US Stocks: Add ".US" suffix
 * - Apple: "AAPL.US"
 * - Microsoft: "MSFT.US"
 * - Tesla: "TSLA.US"
 * - Google: "GOOGL.US"
 * 
 * Polish Stocks: No suffix needed
 * - PKO Bank: "PKO"
 * - CD Projekt: "CDR"
 * 
 * Other markets: Check stooq.com for the correct format
 * 
 * DATE RANGES:
 * - API returns data for trading days only (excludes weekends/holidays)
 * - Maximum historical data varies by ticker
 * - Recent data is most reliable
 * 
 * BEST PRACTICES:
 * 1. Always handle Result.Error cases
 * 2. Use coroutines (lifecycleScope, viewModelScope, etc.)
 * 3. Check if ticker exists in result before accessing
 * 4. Remember dates are in LocalDate format (not strings)
 * 5. Volume is in Long format (can be very large numbers)
 */
