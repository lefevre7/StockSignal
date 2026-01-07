package com.example.stocksignal.data.stooq.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.stocksignal.data.stooq.model.IntradayStockData
import com.example.stocksignal.data.stooq.model.IntradayStockDataMap
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.StockData
import com.example.stocksignal.data.stooq.model.StockDataMap
import com.example.stocksignal.data.stooq.network.StooqApi
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Repository for fetching stock data from Stooq.
 * Handles data fetching, parsing, and error handling with coroutines.
 *
 * @property api Retrofit API interface for Stooq
 */
class StooqRepository(private val api: StooqApi) {

    companion object {
        private const val TAG = "StooqRepository"
        @RequiresApi(Build.VERSION_CODES.O)
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
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getData(
        tickers: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<StockDataMap> {
        return try {
            val startDateStr = startDate.format(DATE_FORMATTER)
            val endDateStr = endDate.format(DATE_FORMATTER)

            // Fetch data for all tickers sequentially to avoid rate limiting
            val results = tickers.map { ticker ->
                ticker to fetchDataForTicker(ticker, startDateStr, endDateStr)
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
    @RequiresApi(Build.VERSION_CODES.O)
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
            val results = tickers.map { ticker ->
                ticker to fetchIntradayDataForTicker(ticker, intervalMinutes, start, end)
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
    @RequiresApi(Build.VERSION_CODES.O)
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
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchIntradayDataForTicker(
        ticker: String,
        intervalMinutes: Int,
        start: LocalDateTime?,
        end: LocalDateTime?
    ): Result<Map<LocalDateTime, IntradayStockData>> {
        return try {
            Log.d(TAG, "Fetching intraday data for ticker=$ticker, interval=$intervalMinutes, start=$start, end=$end")
            val rawResponse = api.getIntradayData(ticker, intervalMinutes)
            Log.d(TAG, "Raw intraday response for $ticker (length=${rawResponse.length}):")
            Log.d(TAG, "--- START RAW RESPONSE ---")
            Log.d(TAG, rawResponse)
            Log.d(TAG, "--- END RAW RESPONSE ---")
            
            val parsedData = parseIntradayResponse(ticker, rawResponse)
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
    @RequiresApi(Build.VERSION_CODES.O)
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
                    val volume = record.get("Volume").toLong()

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

    @RequiresApi(Build.VERSION_CODES.O)
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

        Log.d(TAG, "filterIntradayByRange: filtered to ${filtered.size} records")
        return filtered
    }

    /**
     * Parses the intraday response from stooq.com.
     * 
     * The response format is:
     * - HTML metadata prefix (optional)
     * - After "~TICKER_NAME~" or similar marker, raw CSV data begins
     * - NO header row - data is: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume
     * 
     * Example line: 20260106,154000,446.3800,448.2500,438.4100,439.3908,5400955
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseIntradayResponse(
        ticker: String,
        rawResponse: String
    ): Map<LocalDateTime, IntradayStockData> {
        Log.d(TAG, "Parsing intraday response for $ticker (response length: ${rawResponse.length})")
        
        val dataMap = mutableMapOf<LocalDateTime, IntradayStockData>()
        
        // Stooq intraday format: data starts after "~TICKER_NAME~" marker
        // Format: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume (no header)
        val tildeIndex = rawResponse.lastIndexOf('~')
        val csvData = if (tildeIndex >= 0) {
            Log.d(TAG, "Found ~ marker at index $tildeIndex, extracting data after it")
            rawResponse.substring(tildeIndex + 1).trim()
        } else {
            // Fallback: try to find first line that looks like data (starts with digit)
            Log.d(TAG, "No ~ marker found, looking for data lines starting with digits")
            rawResponse.lines()
                .dropWhile { line -> !line.trimStart().firstOrNull()?.isDigit()!! }
                .joinToString("\n")
        }
        
        Log.d(TAG, "CSV data to parse (first 500 chars): ${csvData.take(500)}")
        
        if (csvData.isBlank()) {
            Log.w(TAG, "No CSV data found in intraday response for $ticker")
            return emptyMap()
        }
        
        var recordCount = 0
        var successCount = 0
        var failureCount = 0
        
        for (line in csvData.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue
            
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
}
