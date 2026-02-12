package com.example.stocksignal.data.stooq.repository

import android.util.Log
import com.example.stocksignal.data.stooq.model.EnrichedIntradayResponse
import com.example.stocksignal.data.stooq.model.IntradayStockData
import com.example.stocksignal.data.stooq.model.IntradayStockDataMap
import com.example.stocksignal.data.stooq.model.PremarketQuote
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.StockData
import com.example.stocksignal.data.stooq.model.StockDataMap
import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.data.stooq.network.StooqBlockedException
import com.example.stocksignal.data.stooq.parser.PremarketQuoteParser
import kotlinx.coroutines.delay
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Repository for fetching stock data from Stooq.
 * Handles data fetching, parsing, and error handling with coroutines.
 *
 * @property api Retrofit API interface for Stooq
 */
class StooqRepository(private val api: StooqApi) {

    companion object {
        private const val TAG = "StooqRepository"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val CSV_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val INTRADAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss")
    }

    /**
     * Fetches stock data for multiple tickers in parallel.
     * Port of Python's StooqDataFetcher.get_data() method.
     *
     * @param tickers List of ticker symbols to fetch
     * @param startDate Start date for data range
     * @param endDate End date for data range
     * @return Result containing StockDataMap or Error
     */
    suspend fun getData(
        tickers: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<StockDataMap> {
        return try {
            val startDateStr = startDate.format(DATE_FORMATTER)
            val endDateStr = endDate.format(DATE_FORMATTER)

            // Fetch data for all tickers sequentially to avoid rate limiting
            val results = mutableListOf<Pair<String, Result<Map<LocalDate, StockData>>>>()
            for (ticker in tickers) {
                val result = fetchDataForTicker(ticker, startDateStr, endDateStr)
                results.add(ticker to result)
                if (result is Result.Error && isTerminalStooqFailure(result.exception)) {
                    Log.w(TAG, "Stopping daily fetch batch early after terminal Stooq failure for $ticker")
                    break
                }
                
                // Random delay between 1-3 seconds to respect rate limits
                if (ticker != tickers.last()) {
                    val delayMs = Random.nextLong(1000, 3001)
                    Log.d(TAG, "Rate limit delay: ${delayMs}ms before next ticker")
                    delay(delayMs)
                }
            }

            // Separate successful and failed fetches
            val successfulData = mutableMapOf<String, Map<LocalDate, StockData>>()
            val failedTickers = mutableListOf<String>()

            results.forEach { (ticker, result) ->
                when (result) {
                    is Result.Success -> successfulData[ticker] = result.data
                    is Result.Error -> {
                        failedTickers.add(ticker)
                        Log.e(
                            TAG,
                            "Failed to fetch data for ticker $ticker from " +
                                    "${startDate.format(CSV_DATE_FORMATTER)} to " +
                                    "${endDate.format(CSV_DATE_FORMATTER)}",
                            result.exception
                        )
                    }
                }
            }

            if (failedTickers.isNotEmpty()) {
                Log.w(
                    TAG,
                    "No data has been fetched for the following tickers: ${failedTickers.joinToString(", ")}"
                )
            }

            if (successfulData.isEmpty()) {
                Result.Error(
                    Exception("No data could be fetched for any ticker"),
                    "Failed to fetch data for all ${tickers.size} tickers"
                )
            } else {
                Result.Success(successfulData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in getData", e)
            Result.Error(e, "Failed to fetch stock data: ${e.message}")
        }
    }

    /**
     * Fetches intraday stock data for multiple tickers in parallel.
     *
     * Example endpoint: https://stooq.com/q/a2/d/?s=tsla.us&i=10
     *
     * Note: Stooq intraday responses may include a preamble before the CSV header. This
     * implementation finds the first `Date,Time` header and parses rows after it.
     *
     * @param tickers List of ticker symbols to fetch
     * @param intervalMinutes Intraday interval in minutes (default: 10)
     * @param start Optional inclusive start timestamp filter (applied after fetch)
     * @param end Optional inclusive end timestamp filter (applied after fetch)
     * @return Result containing IntradayStockDataMap or Error
     */
    suspend fun getIntradayData(
        tickers: List<String>,
        intervalMinutes: Int = 10,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null
    ): Result<IntradayStockDataMap> {
        return try {
            require(intervalMinutes > 0) { "intervalMinutes must be > 0" }
            if (start != null && end != null) {
                require(!start.isAfter(end)) { "start must be <= end" }
            }

            // Fetch data for all tickers sequentially to avoid rate limiting
            val results = mutableListOf<Pair<String, Result<Map<LocalDateTime, IntradayStockData>>>>()
            for (ticker in tickers) {
                val result = fetchIntradayDataForTicker(ticker, intervalMinutes, start, end)
                results.add(ticker to result)
                if (result is Result.Error && isTerminalStooqFailure(result.exception)) {
                    Log.w(TAG, "Stopping intraday fetch batch early after terminal Stooq failure for $ticker")
                    break
                }
                
                // Random delay between 1-3 seconds to respect rate limits
                if (ticker != tickers.last()) {
                    val delayMs = Random.nextLong(1000, 3001)
                    Log.d(TAG, "Rate limit delay: ${delayMs}ms before next ticker")
                    delay(delayMs)
                }
            }

            val successfulData = mutableMapOf<String, Map<LocalDateTime, IntradayStockData>>()
            val failedTickers = mutableListOf<String>()

            results.forEach { (ticker, result) ->
                when (result) {
                    is Result.Success -> successfulData[ticker] = result.data
                    is Result.Error -> {
                        failedTickers.add(ticker)
                        Log.e(
                            TAG,
                            "Failed to fetch intraday data for ticker $ticker (i=$intervalMinutes)",
                            result.exception
                        )
                    }
                }
            }

            if (failedTickers.isNotEmpty()) {
                Log.w(
                    TAG,
                    "No intraday data has been fetched for the following tickers: ${failedTickers.joinToString(", ")}"
                )
            }

            if (successfulData.isEmpty()) {
                Result.Error(
                    Exception("No intraday data could be fetched for any ticker"),
                    "Failed to fetch intraday data for all ${tickers.size} tickers"
                )
            } else {
                Result.Success(successfulData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in getIntradayData", e)
            Result.Error(e, "Failed to fetch intraday stock data: ${e.message}")
        }
    }

    /**
     * Fetches and parses data for a single ticker.
     * Port of Python's _get_data_for_ticker() method.
     *
     * @param ticker Stock ticker symbol
     * @param startDate Start date in YYYYMMDD format
     * @param endDate End date in YYYYMMDD format
     * @return Result containing map of LocalDate to StockData or Error
     */
    private suspend fun fetchDataForTicker(
        ticker: String,
        startDate: String,
        endDate: String
    ): Result<Map<LocalDate, StockData>> {
        return try {
            val csvData = api.getStockData(ticker, startDate, endDate)
            val parsedData = parseCsvData(ticker, csvData)
            Result.Success(parsedData)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching data for ticker $ticker", e)
            Result.Error(e, "Failed to fetch data for ticker $ticker: ${e.message}")
        }
    }

    /**
     * Fetches and parses intraday data for a single ticker.
     *
     * @param ticker Stock ticker symbol
     * @param intervalMinutes Intraday interval in minutes
     * @param start Optional inclusive start timestamp filter (applied after fetch)
     * @param end Optional inclusive end timestamp filter (applied after fetch)
     * @return Result containing map of LocalDateTime to IntradayStockData or Error
     */
    private suspend fun fetchIntradayDataForTicker(
        ticker: String,
        intervalMinutes: Int,
        start: LocalDateTime?,
        end: LocalDateTime?
    ): Result<Map<LocalDateTime, IntradayStockData>> {
        return try {
            Log.d(TAG, "Fetching intraday data for ticker=$ticker, interval=$intervalMinutes, start=$start, end=$end")
            val rawResponse = api.getIntradayData(ticker.lowercase(), intervalMinutes)
            Log.d(TAG, "Raw intraday response for $ticker (length=${rawResponse.length}):")
            Log.d(TAG, "--- START RAW RESPONSE ---")
            Log.d(TAG, rawResponse)
            Log.d(TAG, "--- END RAW RESPONSE ---")
            
            val parsedData = parseIntradayData(ticker, rawResponse)
            Log.d(TAG, "Parsed ${parsedData.size} intraday records for $ticker")
            
            val filtered = filterIntradayByRange(parsedData, start, end)
            Log.d(TAG, "After filtering: ${filtered.size} intraday records for $ticker")

            if (filtered.isEmpty()) {
                Log.w(TAG, "No intraday data rows after parsing/filtering for ticker $ticker")
                Result.Error(
                    Exception("No intraday data rows parsed for ticker $ticker"),
                    "No intraday data found for ticker $ticker (i=$intervalMinutes)"
                )
            } else {
                Log.i(TAG, "Successfully fetched intraday data for $ticker: ${filtered.size} records")
                Result.Success(filtered)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching intraday data for ticker $ticker", e)
            Result.Error(e, "Failed to fetch intraday data for ticker $ticker: ${e.message}")
        }
    }

    /**
     * Fetches enriched intraday data (with exchange info) for a single ticker.
     */
    suspend fun getEnrichedIntradayData(
        ticker: String,
        intervalMinutes: Int = 10,
        start: LocalDateTime? = null,
        end: LocalDateTime? = null
    ): Result<EnrichedIntradayResponse> {
        return try {
            Log.d(TAG, "Fetching enriched intraday data for ticker=$ticker, interval=$intervalMinutes")
            val rawResponse = api.getIntradayData(ticker.lowercase(), intervalMinutes)
            val enriched = parseIntradayResponseEnriched(ticker, rawResponse)
            val filtered = filterIntradayByRange(enriched.data, start, end)
            
            if (filtered.isEmpty()) {
                Log.w(TAG, "No enriched intraday data after filtering for $ticker")
                Result.Error(
                    Exception("No intraday data after filtering for $ticker"),
                    "No intraday data found for $ticker"
                )
            } else {
                Result.Success(EnrichedIntradayResponse(filtered, enriched.exchange))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching enriched intraday data for $ticker", e)
            Result.Error(e, "Failed to fetch enriched data for $ticker: ${e.message}")
        }
    }

    /**
     * Parses CSV data from Stooq into a map of LocalDate to StockData.
     *
     * Expected CSV format:
     * Date,Open,High,Low,Close,Volume
     * 2020-04-01,20.8531,20.9742,20.4063,20.6669,3176696
     *
     * @param ticker Stock ticker symbol
     * @param csvData Raw CSV string from API
     * @return Map of LocalDate to StockData
     */
    private fun parseCsvData(ticker: String, csvData: String): Map<LocalDate, StockData> {
        val dataMap = mutableMapOf<LocalDate, StockData>()

        val csvParser = CSVParser(
            StringReader(csvData),
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build()
        )

        csvParser.use { parser ->
            for (record in parser) {
                try {
                    val date = LocalDate.parse(record.get("Date"), CSV_DATE_FORMATTER)
                    val open = record.get("Open").toDouble()
                    val high = record.get("High").toDouble()
                    val low = record.get("Low").toDouble()
                    val close = record.get("Close").toDouble()
                    // Parse volume as Double first (handles decimal values), then convert to Long
                    val volume = record.get("Volume").toDouble().toLong()

                    dataMap[date] = StockData(
                        date = date,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse CSV record for ticker $ticker: ${record.toMap()}", e)
                }
            }
        }

        return dataMap
    }

    private fun filterIntradayByRange(
        data: Map<LocalDateTime, IntradayStockData>,
        start: LocalDateTime?,
        end: LocalDateTime?
    ): Map<LocalDateTime, IntradayStockData> {
        if (data.isEmpty()) {
            Log.d(TAG, "filterIntradayByRange: input data is empty")
            return emptyMap()
        }
        if (start == null && end == null) {
            Log.d(TAG, "filterIntradayByRange: no filtering needed (start and end are null)")
            return data
        }

        Log.d(TAG, "filterIntradayByRange: filtering ${data.size} records with start=$start, end=$end")
        val sortedKeys = data.keys.sorted()
        val filtered = LinkedHashMap<LocalDateTime, IntradayStockData>()

        for (key in sortedKeys) {
            if (start != null && key.isBefore(start)) continue
            if (end != null && key.isAfter(end)) continue
            filtered[key] = data.getValue(key)
        }

        if (filtered.isEmpty()) {
            val startDate = start?.toLocalDate()
            val endDate = end?.toLocalDate()
            if (startDate != null || endDate != null) {
                Log.w(TAG, "filterIntradayByRange: time filter returned 0 records, falling back to date-only filter")
                val dateFiltered = LinkedHashMap<LocalDateTime, IntradayStockData>()
                for (key in sortedKeys) {
                    val date = key.toLocalDate()
                    if (startDate != null && date.isBefore(startDate)) continue
                    if (endDate != null && date.isAfter(endDate)) continue
                    dateFiltered[key] = data.getValue(key)
                }
                Log.d(TAG, "filterIntradayByRange: date-only filtered to ${dateFiltered.size} records")
                if (dateFiltered.isNotEmpty()) {
                    return dateFiltered
                }
            }
        }

        Log.d(TAG, "filterIntradayByRange: filtered to ${filtered.size} records")
        return filtered
    }

    /**
     * Parses the intraday response from stooq.com, extracting both stock data and exchange info.
     * 
     * The response format is:
     * - HTML metadata prefix with exchange info: <a href=...>NASDAQ</a>: TSLA.US
     * - After "~TICKER_NAME~" or similar marker, raw CSV data begins
     * - NO header row - data is: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume
     * 
     * Example line: 20260106,154000,446.3800,448.2500,438.4100,439.3908,5400955
     */
    private fun parseIntradayResponseEnriched(
        ticker: String,
        rawResponse: String
    ): EnrichedIntradayResponse {
        // Extract exchange from HTML header (e.g., "<a href=...>NASDAQ</a>")
        val exchange = extractExchangeFromResponse(rawResponse)
        val data = parseIntradayData(ticker, rawResponse)
        return EnrichedIntradayResponse(data, exchange)
    }

    private fun extractExchangeFromResponse(rawResponse: String): String? {
        return try {
            // Look for pattern: <a href=...>EXCHANGE_NAME</a>: TICKER
            val regex = """<a[^>]*>([A-Z]+)</a>\s*:""".toRegex()
            val match = regex.find(rawResponse)
            match?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract exchange from intraday response", e)
            null
        }
    }

    private fun parseIntradayData(
        ticker: String,
        rawResponse: String
    ): Map<LocalDateTime, IntradayStockData> {
        Log.d(TAG, "Parsing intraday response for $ticker (response length: ${rawResponse.length})")
        
        val dataMap = mutableMapOf<LocalDateTime, IntradayStockData>()
        
        // Stooq intraday format: data starts after "~TICKER_NAME~" marker
        // Format: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume (no header)
        // The first data row may be on the same line as the header after the last tilde
        val tildeIndex = rawResponse.lastIndexOf('~')
        val afterTilde = if (tildeIndex >= 0) {
            Log.d(TAG, "Found ~ marker at index $tildeIndex, extracting data after it")
            rawResponse.substring(tildeIndex + 1).trim()
        } else {
            // Fallback: try to find first line that looks like data (starts with digit)
            Log.d(TAG, "No ~ marker found, looking for data lines starting with digits")
            rawResponse.lines()
                .dropWhile { line -> line.trimStart().firstOrNull()?.isDigit() != true }
                .joinToString("\n")
        }
        
        // Split into lines - the first line after tilde might contain data
        val allLines = afterTilde.lines()
        
        Log.d(TAG, "CSV data to parse (first 500 chars): ${afterTilde.take(500)}")
        
        // Check for __nodata__ marker which indicates no data available
        if (afterTilde.isBlank() || afterTilde.trim().equals("__nodata__", ignoreCase = true)) {
            Log.i(TAG, "No intraday data available for $ticker (market may be closed or ticker has no intraday data)")
            return emptyMap()
        }
        
        var recordCount = 0
        var successCount = 0
        var failureCount = 0
        
        for (line in allLines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue
            
            // Skip __nodata__ marker if it appears in the data
            if (trimmedLine.equals("__nodata__", ignoreCase = true)) {
                Log.d(TAG, "Skipping __nodata__ marker")
                continue
            }
            
            recordCount++
            try {
                val parts = trimmedLine.split(',')
                if (parts.size < 6) {
                    Log.w(TAG, "Skipping line with insufficient columns (${parts.size}): $trimmedLine")
                    failureCount++
                    continue
                }
                
                // Format: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume
                val dateStr = parts[0].trim()  // e.g., "20260106"
                val timeStr = parts[1].trim().padStart(6, '0')  // e.g., "154000"
                
                val date = LocalDate.parse(dateStr, DATE_FORMATTER)
                val time = LocalTime.parse(timeStr, INTRADAY_TIME_FORMATTER)
                val dateTime = LocalDateTime.of(date, time)
                
                val open = parts[2].trim().toDouble()
                val high = parts[3].trim().toDouble()
                val low = parts[4].trim().toDouble()
                val close = parts[5].trim().toDouble()
                val volume = if (parts.size > 6) parts[6].trim().toLongOrNull() ?: 0L else 0L
                
                dataMap[dateTime] = IntradayStockData(
                    dateTime = dateTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    openInterest = null,
                    annotation = null
                )
                successCount++
                
                if (successCount <= 3) {
                    Log.d(TAG, "Parsed record #$successCount for $ticker: $dateTime, O=$open, H=$high, L=$low, C=$close, V=$volume")
                }
            } catch (e: Exception) {
                failureCount++
                if (failureCount <= 3) {
                    Log.w(TAG, "Failed to parse intraday line #$recordCount for ticker $ticker: $trimmedLine", e)
                }
            }
        }
        
        Log.i(TAG, "Intraday parsing for $ticker: $recordCount total lines, $successCount successful, $failureCount failed")

        val sortedKeys = dataMap.keys.sorted()
        val sortedMap = LinkedHashMap<LocalDateTime, IntradayStockData>(sortedKeys.size)
        for (key in sortedKeys) {
            sortedMap[key] = dataMap.getValue(key)
        }
        
        Log.d(TAG, "Returning ${sortedMap.size} sorted intraday records for $ticker")
        if (sortedMap.isNotEmpty()) {
            Log.d(TAG, "Date range: ${sortedKeys.first()} to ${sortedKeys.last()}")
        }
        
        return sortedMap
    }

    private fun getOptionalCsvValue(record: org.apache.commons.csv.CSVRecord, header: String): String? {
        return try {
            record.get(header).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches stock overview/fundamental data from Stooq quote page.
     * 
     * @param ticker Stock ticker symbol (e.g., "TSLA.US", "COST.US")
     * @return Result containing StockOverview or Error
     */
    suspend fun getStockOverview(ticker: String): Result<com.example.stocksignal.domain.model.StockOverview> {
        return try {
            Log.d(TAG, "Fetching overview for $ticker")
            val html = api.getStockOverview(ticker)
            val overview = com.example.stocksignal.data.stooq.parser.StockOverviewParser.parse(html, ticker)
            Log.i(TAG, "Successfully parsed overview for $ticker")
            Result.Success(overview)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch overview for $ticker", e)
            Result.Error(e, "Failed to fetch stock overview: ${e.message}")
        }
    }

    /**
     * Fetches premarket bid/ask/volume snapshot for multiple tickers.
     *
     * Example endpoint: https://stooq.com/q/?s=nvda.us
     */
    suspend fun getPremarketQuotes(
        tickers: List<String>
    ): Result<Map<String, PremarketQuote>> {
        return try {
            val results = mutableListOf<Pair<String, Result<PremarketQuote>>>()
            for (ticker in tickers) {
                val result = fetchPremarketQuoteForTicker(ticker)
                results.add(ticker to result)
                if (result is Result.Error && isTerminalStooqFailure(result.exception)) {
                    Log.w(TAG, "Stopping premarket quote batch early after terminal Stooq failure for $ticker")
                    break
                }
                if (ticker != tickers.last()) {
                    val delayMs = Random.nextLong(1000, 3001)
                    Log.d(TAG, "Rate limit delay: ${delayMs}ms before next ticker")
                    delay(delayMs)
                }
            }

            val successfulData = mutableMapOf<String, PremarketQuote>()
            val failedTickers = mutableListOf<String>()
            results.forEach { (ticker, result) ->
                when (result) {
                    is Result.Success -> successfulData[ticker] = result.data
                    is Result.Error -> {
                        failedTickers.add(ticker)
                        Log.e(TAG, "Failed to fetch premarket quote for $ticker", result.exception)
                    }
                }
            }

            if (failedTickers.isNotEmpty()) {
                Log.w(TAG, "Premarket quote failures: ${failedTickers.joinToString(", ")}")
            }

            if (successfulData.isEmpty()) {
                Result.Error(
                    Exception("No premarket quotes fetched"),
                    "Failed to fetch premarket quotes for ${tickers.size} tickers"
                )
            } else {
                Result.Success(successfulData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in getPremarketQuotes", e)
            Result.Error(e, "Failed to fetch premarket quotes: ${e.message}")
        }
    }

    private suspend fun fetchPremarketQuoteForTicker(
        ticker: String
    ): Result<PremarketQuote> {
        return try {
            Log.d(TAG, "Fetching premarket quote for $ticker")
            val html = api.getQuotePage(ticker.lowercase())
            val quote = PremarketQuoteParser.parse(html, ticker)
            if (quote == null) {
                Result.Error(
                    Exception("No bid/ask data for $ticker"),
                    "No premarket quote data for $ticker"
                )
            } else {
                Result.Success(quote)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching premarket quote for $ticker", e)
            Result.Error(e, "Failed to fetch premarket quote for $ticker: ${e.message}")
        }
    }

    private fun isTerminalStooqFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SocketTimeoutException || current is StooqBlockedException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
